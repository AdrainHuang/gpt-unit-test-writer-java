package com.example.demo.worker;

import com.example.demo.config.ApplicationProperties;
import com.example.demo.config.OpenAIProperties;
import com.example.demo.exception.BudgetExceededException;
import com.example.demo.model.CoverageDetails;
import com.example.demo.model.MethodCoverage;
import com.example.demo.model.MethodDetails;
import com.example.demo.model.Step;
import com.example.demo.model.openai.Message;
import com.example.demo.model.openai.OpenAIResult;
import com.example.demo.service.OpenAIApiService;
import com.example.demo.utils.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.example.demo.utils.FileUtils.changeToSystemFileSeparator;

@Service
public class CoverageWriter {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final JaCoCoReportAnalyzer analyzer = new JaCoCoReportAnalyzer();
    private final String javaSrcPath = "/src/main/java/";
    private CoverageDetailExtractor extractor;
    @Autowired
    private ApplicationProperties applicationProperties;
    @Autowired
    private OpenAIApiService openAIApiService;
    @Autowired
    private OpenAIProperties openAIProperties;
    @Value("classpath:prompts/coverage_exists.txt")
    private Resource coverageExistsResource;
    @Value("classpath:prompts/coverage_new.txt")
    private Resource coverageNewResource;
    @Value("classpath:prompts/error_feedback.txt")
    private Resource errorFeedbackResource;
    private String projectPath;
    private String projectInfo;
    private String projectBasePackage;
    private double budget;
    private ArrayList<Message> messages;
    private String testFilePath;
    private boolean hasTestFile;

    public void setProjectPath(String projectPath) throws Exception {
        this.projectPath = projectPath;
        ProjectInfoExtractor projectInfoExtractor = new ProjectInfoExtractor(projectPath +
                changeToSystemFileSeparator("/pom.xml"));
        this.projectBasePackage = projectInfoExtractor.extractBasePackage();
        this.projectInfo = projectInfoExtractor.getProjectInfo();
    }

    public void generateUnitTest() throws Exception {
        logger.info("start to run mvn test for: " + projectPath);
        CommandUtils.runMvnTest(projectPath);

        extractor = new CoverageDetailExtractor(projectPath);

        logger.info("start to analyze jacoco report");
        Map<String, List<MethodCoverage>> lowCoverageMethods = analyzer.analyzeReport(projectPath);

        budget = openAIProperties.getProjectBudget();
        try {
            for (String classPathName : lowCoverageMethods.keySet()) {
                handleClass(classPathName, lowCoverageMethods.get(classPathName));
            }
        } catch (BudgetExceededException e) {
            logger.error("Exceed project budget.", e);
        }
    }

    protected void handleClass(String classPathName, List<MethodCoverage> methods) throws Exception {
        logger.info("Low coverage methods in class: " + classPathName + ":");

        if (classPathName.contains("$")) {
            // ignore nested class
            return;
        }

        String javaFilePath = new File(projectPath, changeToSystemFileSeparator(javaSrcPath + classPathName + ".java")).getPath();
        String[] classPathSegments = classPathName.split("/");
        String className = classPathSegments[classPathSegments.length - 1];

        Map<String, MethodDetails> methodDetailsMap = JavaFileUtils.extractMethodCode(javaFilePath, className);
        CoverageDetails coverageDetails = extractor.getCoverageDetails(classPathName);

        for (MethodCoverage method : methods) {
            String methodName = method.getMethodName();
            logger.info("\tmethodName: " + methodName);
            if (methodName.contains("$") || methodName.contains("<")) {
                // ignore nested method
                continue;
            }
            MethodDetails details = methodDetailsMap.get(methodName);
            handleMethod(javaFilePath, classPathName, details, coverageDetails);
        }
    }

    protected void handleMethod(String javaFilePath, String classPathName, MethodDetails details, CoverageDetails coverageDetails) throws IOException, InterruptedException {
        if (details == null) {
            return;
        }

        AbstractMap.SimpleEntry<String, String> coverageLines = CoverageUtils.filterAndConvertCoverageLines(details, coverageDetails);
        String notCoveredLinesString = coverageLines.getKey();
        String partlyCoveredLinesString = coverageLines.getValue();

        for (Step step : applicationProperties.getSteps().get("coverage")) {
            logger.info("Start to generate unit test.");
            createInitialMessages();

            // Define the path of the test file
            testFilePath = projectPath + changeToSystemFileSeparator("/src/test/java/" + classPathName + "Test.java");
            hasTestFile = new File(testFilePath).exists();

            CustomClassExtractor customClassExtractor = new CustomClassExtractor();
            String javaSrcFullPath = changeToSystemFileSeparator(projectPath + javaSrcPath);
            String classSignatures = customClassExtractor.extractCustomClassSignature(javaSrcFullPath, projectBasePackage, javaFilePath, details.getMethodName());
            String prompt = preparePrompt(classPathName, details, notCoveredLinesString, partlyCoveredLinesString, classSignatures);
            String errMsg = handleCoverageStep(classPathName, step, prompt);

            if (errMsg != null && step.getFeedback().equals("true")) {
                logger.info("Start to feedback error.");
                // Load error_feedback.txt as a prompt
                String feedbackPromptTemplate = loadTemplate("feedback");
                String feedbackPrompt = String.format(feedbackPromptTemplate, errMsg);
                handleCoverageStep(classPathName, step, feedbackPrompt);
            }
        }
    }

    protected String handleCoverageStep(String classPathName, Step step, String prompt) throws IOException, InterruptedException {
        if (budget <= 0) {
            throw new BudgetExceededException("Budget exceeded. Ending execution.");
        }
        String errMsg = null;

        // Backup the original file
        ChangeHelper changeHelper = new ChangeHelper(testFilePath, hasTestFile);
        changeHelper.backupFile();

        addUserMessages(prompt);
        // Call to OpenAI API with the prompt here, and get the generated test
        OpenAIResult result = openAIApiService.generateUnitTest(step, messages, hasTestFile);
        budget = budget - result.getCost();
        logger.info("Remain budget is " + budget);
        String codeBlock = FileUtils.extractMarkdownCodeBlocks(result.getContent());

        if (codeBlock == null) {
            errMsg = "Expect one code block in openAI response but it's not.";
            logger.info(errMsg);
        } else {
            // Write the test
            JavaFileUtils.writeTest(codeBlock, testFilePath, classPathName);
            // Run the test
            errMsg = CommandUtils.runTest(projectPath, classPathName);
        }
        if (errMsg == null) {
            changeHelper.complete();
        } else {
            changeHelper.rollbackChanges();
        }
        return errMsg;
    }

    protected String preparePrompt(String classPathName, MethodDetails details, String notCoveredLinesString, String partlyCoveredLinesString, String classSignatures) throws IOException {
        String promptTemplate = loadTemplate(hasTestFile ? "exists" : "new");

        String extraInfo = "";
        String prompt = "";

        if (!partlyCoveredLinesString.isEmpty()) {
            extraInfo += "The following lines are partly covered:\n" + partlyCoveredLinesString + "\n\n";
        }

        if (classSignatures.isEmpty()) {
            extraInfo += "All custom classes in " + projectBasePackage + " package only have default constructor.\n";
        } else {
            extraInfo += "Below custom classes in " + projectBasePackage + " package have custom constructor, other class only have default constructor.\n" + classSignatures;
        }

        if (hasTestFile) {
            prompt = String.format(promptTemplate, projectInfo, classPathName, details.getCodeWithLineNumbers(),
                    notCoveredLinesString, extraInfo);
        } else {

            prompt = String.format(promptTemplate, projectInfo, classPathName, details.getCodeWithLineNumbers(), extraInfo);
        }

        return prompt;
    }

    protected void createInitialMessages() {
        ArrayList<Message> messages = new ArrayList<>();
        Message systemMessage = new Message();
        systemMessage.setRole("system");
        systemMessage.setContent("You are a super smart java developer.");
        messages.add(systemMessage);
        this.messages = messages;
    }

    protected void addUserMessages(String prompt) {
        Message userMessage = new Message();
        userMessage.setRole("user");
        userMessage.setContent(prompt);
        messages.add(userMessage);
    }

    protected String loadTemplate(String templateType) throws IOException {
        Resource resourceToUse;
        switch (templateType) {
            case "exists":
                resourceToUse = coverageExistsResource;
                break;
            case "new":
                resourceToUse = coverageNewResource;
                break;
            case "feedback":
                resourceToUse = errorFeedbackResource;
                break;
            default:
                throw new IllegalArgumentException("Invalid template type: " + templateType);
        }

        byte[] bytes = FileCopyUtils.copyToByteArray(resourceToUse.getInputStream());
        return new String(bytes, StandardCharsets.UTF_8);
    }

}

