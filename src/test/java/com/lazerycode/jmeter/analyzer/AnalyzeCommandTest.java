package com.lazerycode.jmeter.analyzer;

import com.lazerycode.jmeter.analyzer.parser.AggregatedResponses;
import freemarker.template.TemplateException;
import junit.framework.TestCase;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.NullWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import static com.lazerycode.jmeter.analyzer.config.Environment.ENVIRONMENT;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

/**
 * Tests {@link com.lazerycode.jmeter.analyzer.AnalyzeCommand}
 */
public class AnalyzeCommandTest extends TestCase {

  private File workDir;
  private final boolean cleanup = true; // set this to false if you want to test the results manually
  private static final SimpleDateFormat LOCAL_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd'T'HHmmssZ", Locale.getDefault());
  private static final String PACKAGE_PATH = "/com/lazerycode/jmeter/analyzer/analyzecommand/";
  
  @Override
  protected void setUp() throws Exception {

    workDir = new File(new File(System.getProperty("java.io.tmpdir")), getClass().getName());
    workDir.mkdirs();
    cleanDir(workDir);
  }


  @Override
  protected void tearDown() throws Exception {

    if( cleanup ) {
      cleanDir(workDir);
    }
  }

  //--------------------------------------------------------------------------------------------------------------------

  /**
   * Tests the text output with only successful samples
   */
  public void testTextOutputSuccess() throws Exception {

    String localPackagePath = "/success/";

    setUpEnvironment(false, false, null, null);

    testOutput(localPackagePath);
  }

  /**
   * Tests the text output with a few unsuccessful samples
   */
  public void testTextOutputSomeErrors() throws Exception {

    String localPackagePath = "/someerrors/";

    setUpEnvironment(false,false, null, null);

    testOutput(localPackagePath);
  }

  /**
   * Tests the text output with only unsuccessful samples
   */
  public void testTextOnlyErrors() throws Exception {

    String localPackagePath = "/onlyerrors/";

    setUpEnvironment(false, false, null, null);

    testOutput(localPackagePath);
  }

  /**
   * Tests the text output with an empty results file
   */
  public void testTextEmptyOutput() throws Exception {

    String localPackagePath = "/empty/";

    setUpEnvironment(false,false, null, null);

    testOutput(localPackagePath);
  }

  /**
   * Tests that all result files are available
   *
   * Text, HTML, CSVs and Images
   */
  public void testAllFiles() throws Exception {

    String localPackagePath = "/allfiles/";

    LinkedHashMap<String, String> patterns = new LinkedHashMap<java.lang.String, java.lang.String>();
    patterns.put("page", "/main");
    patterns.put("blob", "/main/**");

    setUpEnvironment(true, true, patterns, null);

    testOutput(localPackagePath);

    List<String> fileNames = Arrays.asList(
            "blob-durations.csv", "blob-durations.png", "blob-sizes.csv",
            "page-durations.csv", "page-durations.png", "page-sizes.csv",
            "summary.html", "summary.txt");

    for(String fileName : fileNames) {
      File expected = new File(getClass().getResource(PACKAGE_PATH+localPackagePath+fileName).getFile());
      File actual = new File(workDir, fileName);
      assertTrue("file"+actual+" doesn't have the right content.", FileUtils.contentEqualsIgnoreEOL(expected, actual, null));
    }

  }

  /**
   * Test output with custom template
   */
  public void testCustomTemplates() throws Exception {

    String localPackagePath = "/testtemplates/";

    //copy template to file system
    File templateDir = new File(workDir,"text");
    templateDir.mkdir();
    File template = initializeFile(templateDir,"main.ftl");

    InputStream is = getClass().getResourceAsStream(PACKAGE_PATH+"testtemplates/text/main.ftl");
    OutputStream os = new FileOutputStream(template);
    while (is.available() > 0) {
        os.write(is.read());
    }
    os.close();
    is.close();


    setUpEnvironment(false, false, null, workDir);

    testOutput(localPackagePath);
  }


  /**
   * tests that file is downloaded and has the right content
   */
  public void testDownload() throws Exception {
    String localPackagePath = "/download/";

    final String start = "20111216T145509+0100";
    final String end = "20111216T145539+0100";
    
    // create a file to be downloaded
    // contains urlencoded timestamps which are formatted as if retrieved from jmeter.xml
    File downloadableFile = initializeFile(workDir, String.format("%s.%s.tmp", toLocal(start), toLocal(end)));
    FileUtils.write(downloadableFile,"contents");

    File downloadablePatternFile = new File(workDir, "_FROM_._TO_.tmp");

    Properties remoteResources = new Properties();
    remoteResources.setProperty(downloadablePatternFile.toURI().toString(), "download.txt");

    setUpEnvironment(false,false, null, null);
    ENVIRONMENT.setRemoteResources(remoteResources);

    testOutput(localPackagePath);

    File downloadedFile = new File(workDir, "download.txt");

    assertTrue("file was not successfully downloaded.", downloadedFile.exists());
    assertTrue("file doesn't have the right content.", FileUtils.contentEquals(downloadableFile, downloadedFile));
  }

  //--------------------------------------------------------------------------------------------------------------------

  private void setUpEnvironment(boolean generateCSVs, boolean generateCharts, LinkedHashMap<String, String> patterns, File templateDirectory) {
    ENVIRONMENT.clear();
    ENVIRONMENT.setGenerateCSVs(generateCSVs);
    ENVIRONMENT.setGenerateCharts(generateCharts);
    ENVIRONMENT.setMaxSamples(1000);
    ENVIRONMENT.setTargetDirectory(workDir);
    ENVIRONMENT.setRequestGroups(patterns);
    ENVIRONMENT.setTemplateDirectory(templateDirectory);
    ENVIRONMENT.initializeFreemarkerConfiguration();
    ENVIRONMENT.setResultRenderHelper(new ResultRenderHelper());
  }

  /**
   * Output test code that is used by most test methods.
   * {@link com.lazerycode.jmeter.analyzer.config.Environment#ENVIRONMENT} must be reset/initialized before calling this method.
   *
   * @param packagePath path relative to {@link #PACKAGE_PATH}
   * @throws Exception
   */
  private void testOutput(String packagePath) throws Exception {

    String localPackagePath = PACKAGE_PATH + packagePath;

    Reader data = new InputStreamReader(getClass().getResourceAsStream(localPackagePath+"jmeter-result.jtl"));

    //commandline output does not matter during tests and is routed to a NullWriter
    Writer writer = new NullWriter();

    new LocalAnalyzeCommand(writer).analyze(data);

    writer.flush();
    writer.close();
    data.close();

    File actualTXT = new File(workDir+"/summary.txt");
    File expectedTXT = new File(getClass().getResource(localPackagePath+"summary.txt").getFile());

    String actualTXTContent = normalizeFileContents(actualTXT);
    String expectedTXTContent = normalizeFileContents(expectedTXT);

    assertThat("lines in TXT file do not match: ",
            actualTXTContent,
            is(equalTo(expectedTXTContent)));

    File actualHTML = new File(workDir+"/summary.html");
    File expectedHTML = new File(getClass().getResource(localPackagePath+"summary.html").getFile());

    String actualHTMLContent = normalizeFileContents(actualHTML);
    String expectedHTMLContent = normalizeFileContents(expectedHTML);

    assertThat("lines in TXT file do not match: ",
            actualHTMLContent,
            is(equalTo(expectedHTMLContent)));
  }

  /**
   * Strip line ends from String contents of a file so that contents can be compared on different platforms.
   *
   * @param file
   * @return normalized String
   * @throws IOException
   */
  private String normalizeFileContents(File file) throws IOException {

    String content = FileUtils.readFileToString(file,"UTF-8");
    content = content.replaceAll("(\\r\\n|\\r|\\n)", "");

    return content;
  }

  /**
   * Remove all contents (including subdirectories) from given directory
   * @param dir
   */
  private void cleanDir(File dir) {

    for( File file : dir.listFiles() ) {
      if(file.isDirectory()) {
        //recurse into directory
        cleanDir(file);
      }

      file.delete();
    }
  }

  /**
   * Create and return file of given name in given directory
   */
  private File initializeFile(File dir, String name) throws IOException {
    File result = new File(dir, name);

    if (!result.getParentFile().mkdirs() && !result.getParentFile().exists()) {
      throw new IOException("Cannot create directories: " + result.getParentFile().getAbsolutePath());
    }

    if (result.exists() && !result.delete()) {
      throw new IOException("Failed to delete file: " + result.getAbsolutePath());
    }

    if (!result.createNewFile()) {
      throw new IOException("Failed to create file: " + result.getAbsolutePath());
    }

    return result;
  }

  private static Date parseDate(String dateString) throws ParseException {
    return LOCAL_DATE_FORMAT.parse(dateString);
  }
  
  private static String toLocal(Date date) {
    return LOCAL_DATE_FORMAT.format(date);
  }
  
  private static String toLocal(String dateString) throws ParseException {
    return toLocal(parseDate(dateString));
  }

  //====================================================================================================================

  private class LocalAnalyzeCommand extends AnalyzeCommand {

    private Writer writer;

    LocalAnalyzeCommand(Writer writer) {
      this.writer = writer;
    }


    @Override
    protected void renderTextToStdOut(Map<String, AggregatedResponses> testResults) throws IOException, TemplateException {
      resultRenderHelper.renderText(testResults, writer);
    }
  }
}
