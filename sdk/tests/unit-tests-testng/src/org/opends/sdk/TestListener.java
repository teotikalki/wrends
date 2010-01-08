/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.sdk;



import org.testng.TestListenerAdapter;
import org.testng.IReporter;
import org.testng.ISuite;
import org.testng.ITestResult;
import org.testng.IClass;
import org.testng.ITestNGMethod;
import org.testng.ITestContext;
import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;
import org.testng.xml.XmlSuite;
import static com.sun.opends.sdk.util.StaticUtils.EOL;

import java.util.*;
import java.io.PrintStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;



/**
 * This class is our replacement for the test results that TestNG
 * generates. It prints out test to the console as they happen.
 */
public class TestListener extends TestListenerAdapter implements
    IReporter
{

  /**
   *
   */
  private static class TestClassResults
  {
    private final IClass _cls;

    private final LinkedHashMap<ITestNGMethod, TestMethodResults> _methods = new LinkedHashMap<ITestNGMethod, TestMethodResults>();

    private int _totalInvocations = 0;

    private long _totalDurationMs = 0;

    // Indexed by SUCCESS, FAILURE, SKIP, SUCCESS_PERCENTAGE_FAILURE
    private int[] _resultCounts = new int[STATUSES.length];



    public TestClassResults(IClass cls)
    {
      _cls = cls;
    }



    private TestMethodResults getResultsForMethod(ITestNGMethod method)
    {
      TestMethodResults results = _methods.get(method);
      if (results == null)
      {
        results = new TestMethodResults(method);
        _methods.put(method, results);
      }
      return results;
    }



    synchronized void addTestResult(ITestResult result)
    {
      _totalInvocations++;
      _totalDurationMs += result.getEndMillis()
          - result.getStartMillis();

      getResultsForMethod(result.getMethod()).addTestResult(result);
      int status = result.getStatus();
      if (status < 0 || status >= _resultCounts.length)
      {
        status = 0;
      }
      _resultCounts[status]++;
    }



    synchronized Collection<TestMethodResults> getAllMethodResults()
    {
      return _methods.values();
    }



    synchronized void getSummaryTimingInfo(StringBuilder timingOutput)
    {
      timingOutput.append(_cls.getRealClass().getName() + "    ");
      timingOutput.append(getTotalDurationMs() + " ms" + " ("
          + getTotalInvocations() + ")");
    }



    synchronized void getTimingInfo(StringBuilder timingOutput)
    {
      getSummaryTimingInfo(timingOutput);
      timingOutput.append(EOL);
      for (TestMethodResults results : getAllMethodResults())
      {
        results.getTimingInfo(timingOutput, false);
      }

      timingOutput.append(EOL);
    }



    long getTotalDurationMs()
    {
      return _totalDurationMs;
    }



    int getTotalInvocations()
    {
      return _totalInvocations;
    }
  }



  /**
   *
   */
  private static class TestMethodResults
  {
    private final ITestNGMethod _method;

    int _totalInvocations = 0;

    long _totalDurationMs = 0;

    // Indexed by SUCCESS, FAILURE, SKIP, SUCCESS_PERCENTAGE_FAILURE
    private int[] _resultCounts = new int[STATUSES.length];



    public TestMethodResults(ITestNGMethod method)
    {
      _method = method;
    }



    synchronized void addTestResult(ITestResult result)
    {
      _totalInvocations++;
      _totalDurationMs += result.getEndMillis()
          - result.getStartMillis();

      int status = result.getStatus();
      if (status < 0 || status >= _resultCounts.length)
      {
        status = 0;
      }
      _resultCounts[status]++;
    }



    synchronized void getTimingInfo(StringBuilder timingOutput,
        boolean includeClassName)
    {
      timingOutput.append("    ");
      if (includeClassName)
      {
        timingOutput.append(_method.getRealClass().getName()).append(
            "#");
      }
      timingOutput.append(_method.getMethodName() + "  ");
      timingOutput.append(_totalDurationMs + " ms" + " ("
          + _totalInvocations + ")");
      if (_resultCounts[ITestResult.FAILURE] > 0)
      {
        timingOutput.append(" " + _resultCounts[ITestResult.FAILURE]
            + " failure(s)");
      }
      timingOutput.append(EOL);
    }
  }



  public static final String REPORT_FILE_NAME = "results.txt";

  // This is used to communicate with build.xml. So that even when a
  // test
  // fails, we can do the coverage report before failing the build.
  public static final String ANT_TESTS_FAILED_FILE_NAME = ".tests-failed-marker";

  private final StringBuilder _bufferedTestFailures = new StringBuilder();

  public static final String PROPERTY_TEST_PROGRESS = "test.progress";

  public static final String TEST_PROGRESS_NONE = "none";

  public static final String TEST_PROGRESS_ALL = "all";

  public static final String TEST_PROGRESS_DEFAULT = "default";

  public static final String TEST_PROGRESS_TIME = "time";

  public static final String TEST_PROGRESS_TEST_COUNT = "count";

  // for
  // now,
  // since
  // it's
  // not
  // useful
  // to
  // most
  // developers

  public static final String TEST_PROGRESS_MEMORY = "memory";

  public static final String TEST_PROGRESS_MEMORY_GCS = "gcs"; // Hidden

  public static final String TEST_PROGRESS_THREAD_COUNT = "threadcount";

  public static final String TEST_PROGRESS_THREAD_CHANGES = "threadchanges";

  private boolean doProgressNone = false;

  private boolean doProgressTime = true;

  private boolean doProgressTestCount = true;

  private boolean doProgressMemory = false;

  private boolean doProgressMemoryGcs = false;

  private boolean doProgressThreadCount = false;

  private boolean doProgressThreadChanges = false;

  private static final String DIVIDER_LINE = "-------------------------------------------------------------------------------"
      + EOL;

  private final static int PAGE_WIDTH = 80;



  public static void pauseOnFailure()
  {
    File tempFile = null;
    try
    {
      tempFile = File.createTempFile("testfailure", "watchdog");
      tempFile.deleteOnExit();
      System.err.println("**** Pausing test execution until file "
          + tempFile.getCanonicalPath() + " is removed.");
    }
    catch (Exception e)
    {
      System.err.println("**** ERROR:  Could not create a watchdog "
          + "file.  Pausing test execution indefinitely.");
      System.err.println("**** You will have to manually kill the "
          + "JVM when you're done investigating the problem.");
    }

    while ((tempFile != null) && tempFile.exists())
    {
      try
      {
        Thread.sleep(100);
      }
      catch (Exception e)
      {
      }
    }

    System.err.println("**** Watchdog file removed.  Resuming test "
        + "case execution.");
  }



  /**
   * Return a String representation of all of the current threads.
   *
   * @return a dump of all Threads on the server
   */
  public static String threadStacksToString()
  {
    Map<Thread, StackTraceElement[]> threadStacks = Thread
        .getAllStackTraces();

    // Re-arrange all of the elements by thread ID so that there is some
    // logical
    // order.
    TreeMap<Long, Map.Entry<Thread, StackTraceElement[]>> orderedStacks = new TreeMap<Long, Map.Entry<Thread, StackTraceElement[]>>();
    for (Map.Entry<Thread, StackTraceElement[]> e : threadStacks
        .entrySet())
    {
      orderedStacks.put(e.getKey().getId(), e);
    }

    final StringBuilder buffer = new StringBuilder();
    for (Map.Entry<Thread, StackTraceElement[]> e : orderedStacks
        .values())
    {
      Thread t = e.getKey();
      StackTraceElement[] stackElements = e.getValue();

      long id = t.getId();

      buffer.append("id=");
      buffer.append(id);
      buffer.append(" ---------- ");
      buffer.append(t.getName());
      buffer.append(" ----------");
      buffer.append(EOL);

      if (stackElements != null)
      {
        for (int j = 0; j < stackElements.length; j++)
        {
          buffer.append("   ").append(stackElements[j].getClassName());
          buffer.append(".");
          buffer.append(stackElements[j].getMethodName());
          buffer.append("(");
          buffer.append(stackElements[j].getFileName());
          buffer.append(":");
          if (stackElements[j].isNativeMethod())
          {
            buffer.append("native");
          }
          else
          {
            buffer.append(stackElements[j].getLineNumber());
          }
          buffer.append(")").append(EOL);
        }
      }
      buffer.append(EOL);
    }

    return buffer.toString();
  }



  private static String center(String header)
  {
    StringBuilder buffer = new StringBuilder();
    int indent = (PAGE_WIDTH - header.length()) / 2;
    for (int i = 0; i < indent; i++)
    {
      buffer.append(" ");
    }
    buffer.append(header);
    return buffer.toString();
  }



  private Set<Class<?>> _checkedForTypeAndAnnotations = new HashSet<Class<?>>();

  private final LinkedHashSet<Class<?>> _classesWithTestsRunInterleaved = new LinkedHashSet<Class<?>>();

  private Object _lastTestObject = null;

  private final IdentityHashMap<Object, Object> _previousTestObjects = new IdentityHashMap<Object, Object>();

  private Set<Method> _checkedForAnnotation = new HashSet<Method>();

  private boolean statusHeaderPrinted = false;

  private final long startTimeMs = System.currentTimeMillis();

  private long prevTimeMs = System.currentTimeMillis();

  private List<String> prevThreads = new ArrayList<String>();

  private long prevMemInUse = 0;

  private long maxMemInUse = 0;

  private final LinkedHashMap<IClass, TestClassResults> _classResults = new LinkedHashMap<IClass, TestClassResults>();

  private static final int NUM_SLOWEST_METHODS = 100;

  private final static String[] STATUSES = { "<<invalid>>", "Success",
      "Failure", "Skip", "Success Percentage Failure" };



  public TestListener() throws Exception
  {
    initializeProgressVars();
  }



  public void generateReport(List<XmlSuite> xmlSuites,
      List<ISuite> suites, String outputDirectory)
  {
    File reportFile = new File(outputDirectory, REPORT_FILE_NAME);

    writeReportToFile(reportFile);
    writeReportToScreen(reportFile);
    writeAntTestsFailedMarker(outputDirectory);
  }



  public void onConfigurationFailure(ITestResult tr)
  {
    super.onConfigurationFailure(tr);

    IClass cls = tr.getTestClass();
    ITestNGMethod method = tr.getMethod();

    String fqMethod = cls.getName() + "#" + method.getMethodName();

    StringBuilder failureInfo = new StringBuilder();
    failureInfo.append("Failed Test:  ").append(fqMethod).append(EOL);
    // Object[] parameters = tr.getParameters();

    Throwable cause = tr.getThrowable();
    if (cause != null)
    {
      failureInfo.append("Failure Cause:  ").append(
          getTestngLessStack(cause));
    }

    failureInfo.append(EOL + EOL);
    System.err.print(EOL + EOL + EOL
        + "         C O N F I G U R A T I O N   F A I L U R E ! ! !"
        + EOL + EOL);
    System.err.print(failureInfo);
    System.err.print(DIVIDER_LINE + EOL + EOL);

    _bufferedTestFailures.append(failureInfo);
  }



  public void onStart(ITestContext testContext)
  {
    super.onStart(testContext);

    // Delete the previous report if it's there.
    new File(testContext.getOutputDirectory(), REPORT_FILE_NAME)
        .delete();
  }



  public void onTestFailedButWithinSuccessPercentage(ITestResult tr)
  {
    super.onTestFailedButWithinSuccessPercentage(tr);
    onTestFinished(tr);
  }



  public void onTestFailure(ITestResult tr)
  {
    super.onTestFailure(tr);

    IClass cls = tr.getTestClass();
    ITestNGMethod method = tr.getMethod();

    String fqMethod = cls.getName() + "#" + method.getMethodName();

    StringBuilder failureInfo = new StringBuilder();
    failureInfo.append("Failed Test:  ").append(fqMethod).append(EOL);
    Object[] parameters = tr.getParameters();

    Throwable cause = tr.getThrowable();
    if (cause != null)
    {
      failureInfo.append("Failure Cause:  ").append(
          getTestngLessStack(cause));
    }

    for (int i = 0; (parameters != null) && (i < parameters.length); i++)
    {
      Object parameter = parameters[i];
      failureInfo.append("parameter[" + i + "]: ").append(parameter)
          .append(EOL);
    }

    failureInfo.append(EOL + EOL);
    System.err.print(EOL + EOL + EOL
        + "                 T E S T   F A I L U R E ! ! !" + EOL + EOL);
    System.err.print(failureInfo);
    System.err.print(DIVIDER_LINE + EOL + EOL);

    _bufferedTestFailures.append(failureInfo);

    String pauseStr = System
        .getProperty("org.opends.test.pauseOnFailure");
    if ((pauseStr != null) && pauseStr.equalsIgnoreCase("true"))
    {
      pauseOnFailure();
    }

    onTestFinished(tr);
  }



  public void onTestSkipped(ITestResult tr)
  {
    super.onTestSkipped(tr);
    onTestFinished(tr);
  }



  public void onTestStart(ITestResult tr)
  {
    super.onTestStart(tr);

    enforceTestClassTypeAndAnnotations(tr);
    checkForInterleavedBetweenClasses(tr);
    enforceMethodHasAnnotation(tr);
  }



  public void onTestSuccess(ITestResult tr)
  {
    super.onTestSuccess(tr);
    onTestFinished(tr);
  }



  private void addTestResult(ITestResult result)
  {
    getResultsForClass(result.getTestClass()).addTestResult(result);

    // Read the comments in DirectoryServerTestCase to understand what's
    // going on here.
    Object[] testInstances = result.getMethod().getInstances();
    for (int i = 0; i < testInstances.length; i++)
    {
      Object testInstance = testInstances[i];
      if (testInstance instanceof OpenDSTestCase)
      {
        OpenDSTestCase openDSTestCase = (OpenDSTestCase) testInstance;
        Object[] parameters = result.getParameters();
        if (result.getStatus() == ITestResult.SUCCESS)
        {
          openDSTestCase.addParamsFromSuccessfulTests(parameters);
          // This can eat up a bunch of memory for tests that are
          // expected to throw
          result.setThrowable(null);
        }
        else
        {
          openDSTestCase.addParamsFromFailedTest(parameters);

          // When the test finishes later on, we might not have
          // everything
          // that we need to print the result (e.g. the Schema for an
          // Entry
          // or DN), so go ahead and convert it to a String now.
          result.setParameters(convertToStringParameters(parameters));
        }
      }
      else
      {
        // We already warned about it.
      }
    }
  }



  private void checkForInterleavedBetweenClasses(ITestResult tr)
  {
    Object[] testInstances = tr.getMethod().getInstances();
    // This will almost always have a single element. If it doesn't,
    // just
    // skip it.
    if (testInstances.length != 1)
    {
      return;
    }

    Object testInstance = testInstances[0];

    // We're running another test on the same test object. Everything is
    // fine.
    if (_lastTestObject == testInstance)
    {
      return;
    }

    // Otherwise, we're running a new test, so save the old one.
    if (_lastTestObject != null)
    {
      _previousTestObjects.put(_lastTestObject, _lastTestObject);
    }

    // Output progress info since we're running a new class
    outputTestProgress(_lastTestObject);

    // And make sure we don't have a test object that we already ran
    // tests with.
    if (_previousTestObjects.containsKey(testInstance))
    {
      _classesWithTestsRunInterleaved.add(testInstance.getClass());
    }

    _lastTestObject = testInstance;
  }



  private String[] convertToStringParameters(Object[] parameters)
  {
    if (parameters == null)
    {
      return null;
    }

    String[] strParams = new String[parameters.length];
    for (int i = 0; i < parameters.length; i++)
    {
      strParams[i] = String.valueOf(parameters[i]).intern();
    }

    return strParams;
  }



  private int countTestMethods()
  {
    int count = 0;
    for (TestClassResults results : _classResults.values())
    {
      count += results._methods.size();
    }
    return count;
  }



  private int countTestsWithStatus(int status)
  {
    int count = 0;
    for (TestClassResults results : _classResults.values())
    {
      count += results._resultCounts[status];
    }
    return count;
  }



  private int countTotalInvocations()
  {
    int count = 0;
    for (TestClassResults results : _classResults.values())
    {
      count += results._totalInvocations;
    }
    return count;
  }



  private void enforceMethodHasAnnotation(ITestResult tr)
  {
    // Only warn once per method.
    Method testMethod = tr.getMethod().getMethod();
    if (_checkedForAnnotation.contains(testMethod))
    {
      return;
    }
    _checkedForAnnotation.add(testMethod);

    Annotation testAnnotation = testMethod.getAnnotation(Test.class);
    Annotation dataProviderAnnotation = testMethod
        .getAnnotation(DataProvider.class);

    if ((testAnnotation == null) && (dataProviderAnnotation == null))
    {
      String errorMessage = "The test method "
          + testMethod
          + " does not have a @Test annotation.  "
          + "However, TestNG assumes it is a test method because it's a public method "
          + "in a class with a class-level @Test annotation.  You can remove this warning by either "
          + "marking the method with @Test or by making it non-public.";
      System.err.println("\n\nWARNING: " + errorMessage + "\n\n");
    }
  }



  private void enforceTestClassTypeAndAnnotations(ITestResult tr)
  {
    Class<?> testClass = null;
    testClass = tr.getMethod().getRealClass();

    // Only warn once per class.
    if (_checkedForTypeAndAnnotations.contains(testClass))
    {
      return;
    }
    _checkedForTypeAndAnnotations.add(testClass);

    if (!OpenDSTestCase.class.isAssignableFrom(testClass))
    {
      String errorMessage = "The test class " + testClass.getName()
          + " must inherit (directly or indirectly) "
          + "from DirectoryServerTestCase.";
      System.err.println("\n\nERROR: " + errorMessage + "\n\n");
      throw new RuntimeException(errorMessage);
    }

    Class<?> classWithTestAnnotation = findClassWithTestAnnotation(testClass);

    if (classWithTestAnnotation == null)
    {
      String errorMessage = "The test class "
          + testClass.getName()
          + " does not have a @Test annotation.  "
          + "All test classes must have a @Test annotation, and this annotation must have "
          + "sequential=true set to ensure that tests for a single class are run together.";
      System.err.println("\n\nERROR: " + errorMessage + "\n\n");
      throw new RuntimeException(errorMessage);
    }

    Test testAnnotation = classWithTestAnnotation
        .getAnnotation(Test.class);
    if (!testAnnotation.sequential())
    {
      // Give an error message that is as specific as possible.
      String errorMessage = "The @Test annotation for class "
          + testClass.getName()
          + (classWithTestAnnotation.equals(testClass) ? " "
              : (", which is declared by class "
                  + classWithTestAnnotation.getName() + ", "))
          + "must include sequential=true to ensure that tests for a single class are run together.";
      System.err.println("\n\nERROR: " + errorMessage + "\n\n");
      throw new RuntimeException(errorMessage);
    }
  }



  // Return the class in cls's inheritence hierarchy that has the @Test
  // annotation defined.
  private Class<?> findClassWithTestAnnotation(Class<?> cls)
  {
    while (cls != null)
    {
      if (cls.getAnnotation(Test.class) != null)
      {
        return cls;
      }
      else
      {
        cls = cls.getSuperclass();
      }
    }
    return null;
  }



  synchronized private List<TestMethodResults> getAllMethodResults()
  {
    List<TestMethodResults> allResults = new ArrayList<TestMethodResults>();
    for (TestClassResults results : _classResults.values())
    {
      allResults.addAll(results.getAllMethodResults());
    }
    return allResults;
  }



  private List<TestClassResults> getClassesDescendingSortedByDuration()
  {
    List<TestClassResults> allClasses = new ArrayList<TestClassResults>(
        _classResults.values());
    Collections.sort(allClasses, new Comparator<TestClassResults>()
    {
      public int compare(TestClassResults o1, TestClassResults o2)
      {
        if (o1._totalDurationMs > o2._totalDurationMs)
        {
          return -1;
        }
        else if (o1._totalDurationMs < o2._totalDurationMs)
        {
          return 1;
        }
        else
        {
          return 0;
        }
      }
    });
    return allClasses;
  }



  private String getFqMethod(ITestResult result)
  {
    IClass cls = result.getTestClass();
    ITestNGMethod method = result.getMethod();

    return cls.getName() + "#" + method.getMethodName();
  }



  private List<TestMethodResults> getMethodsDescendingSortedByDuration()
  {
    List<TestMethodResults> allMethods = getAllMethodResults();
    Collections.sort(allMethods, new Comparator<TestMethodResults>()
    {
      public int compare(TestMethodResults o1, TestMethodResults o2)
      {
        if (o1._totalDurationMs > o2._totalDurationMs)
        {
          return -1;
        }
        else if (o1._totalDurationMs < o2._totalDurationMs)
        {
          return 1;
        }
        else
        {
          return 0;
        }
      }
    });
    return allMethods;
  }



  private TestClassResults getResultsForClass(IClass cls)
  {
    TestClassResults results = _classResults.get(cls);
    if (results == null)
    {
      results = new TestClassResults(cls);
      _classResults.put(cls, results);
    }
    return results;
  }



  private void getSlowestTestsOutput(StringBuilder timingOutput)
  {
    timingOutput.append(center("CLASS SUMMARY SORTED BY DURATION"))
        .append(EOL);
    timingOutput.append(
        center("[class-name total-time (total-invocations)]")).append(
        EOL + EOL);
    List<TestClassResults> sortedClasses = getClassesDescendingSortedByDuration();
    for (int i = 0; i < sortedClasses.size(); i++)
    {
      TestClassResults results = sortedClasses.get(i);
      timingOutput.append("  ");
      results.getSummaryTimingInfo(timingOutput);
      timingOutput.append(EOL);
    }

    timingOutput.append(EOL + DIVIDER_LINE + EOL + EOL);
    timingOutput.append(center("SLOWEST METHODS")).append(EOL);
    timingOutput.append(
        center("[method-name total-time (total-invocations)]")).append(
        EOL + EOL);
    List<TestMethodResults> sortedMethods = getMethodsDescendingSortedByDuration();
    for (int i = 0; i < Math.min(sortedMethods.size(),
        NUM_SLOWEST_METHODS); i++)
    {
      TestMethodResults results = sortedMethods.get(i);
      results.getTimingInfo(timingOutput, true);
    }
  }



  private String getTestngLessStack(Throwable t)
  {
    StackTraceElement[] elements = t.getStackTrace();

    int lowestOpenDSFrame;
    for (lowestOpenDSFrame = elements.length - 1; lowestOpenDSFrame >= 0; lowestOpenDSFrame--)
    {
      StackTraceElement element = elements[lowestOpenDSFrame];
      String clsName = element.getClassName();
      if (clsName.startsWith("org.opends.")
          && !clsName.equals("org.opends.server.SuiteRunner"))
      {
        break;
      }
    }

    StringBuilder buffer = new StringBuilder();
    buffer.append(t).append(EOL);
    for (int i = 0; i <= lowestOpenDSFrame; i++)
    {
      buffer.append("    ").append(elements[i]).append(EOL);
    }

    Throwable cause = t.getCause();
    if (t != null)
    {
      if (cause instanceof InvocationTargetException)
      {
        InvocationTargetException invocation = ((InvocationTargetException) cause);
        buffer.append("Invocation Target Exception: "
            + getTestngLessStack(invocation));
      }
    }

    return buffer.toString();
  }



  private void initializeProgressVars()
  {
    String prop = System.getProperty(PROPERTY_TEST_PROGRESS);
    if (prop == null)
    {
      return;
    }

    prop = prop.toLowerCase();
    List<String> progressValues = Arrays.asList(prop
        .split("\\s*\\W+\\s*"));

    if ((prop.length() == 0) || progressValues.isEmpty())
    {
      // Accept the defaults
    }
    else if (progressValues.contains(TEST_PROGRESS_NONE))
    {
      doProgressNone = true;
      doProgressTime = false;
      doProgressTestCount = false;
      doProgressMemory = false;
      doProgressMemoryGcs = false;
      doProgressThreadCount = false;
      doProgressThreadChanges = false;
    }
    else if (progressValues.contains(TEST_PROGRESS_ALL))
    {
      doProgressNone = false;
      doProgressTime = true;
      doProgressTestCount = true;
      doProgressMemory = true;
      doProgressMemoryGcs = true;
      doProgressThreadCount = true;
      doProgressThreadChanges = true;
    }
    else
    {
      doProgressNone = false;
      doProgressTime = progressValues.contains(TEST_PROGRESS_TIME);
      doProgressTestCount = progressValues
          .contains(TEST_PROGRESS_TEST_COUNT);
      doProgressMemory = progressValues.contains(TEST_PROGRESS_MEMORY);
      doProgressMemoryGcs = progressValues
          .contains(TEST_PROGRESS_MEMORY_GCS);
      doProgressThreadCount = progressValues
          .contains(TEST_PROGRESS_THREAD_COUNT);
      doProgressThreadChanges = progressValues
          .contains(TEST_PROGRESS_THREAD_CHANGES);

      // If we were asked to do the defaults, then restore anything
      // that's on by default
      if (progressValues.contains(TEST_PROGRESS_DEFAULT))
      {
        doProgressTime = true;
        doProgressTestCount = true;
      }
    }
  }



  private List<String> listAllThreadNames()
  {
    Thread currentThread = Thread.currentThread();
    ThreadGroup topGroup = currentThread.getThreadGroup();
    while (topGroup.getParent() != null)
    {
      topGroup = topGroup.getParent();
    }

    Thread threads[] = new Thread[topGroup.activeCount() * 2];
    int numThreads = topGroup.enumerate(threads);

    List<String> activeThreads = new ArrayList<String>();
    for (int i = 0; i < numThreads; i++)
    {
      Thread thread = threads[i];
      if (thread.isAlive())
      {
        String fullName = thread.getName();
        activeThreads.add(fullName);
      }
    }

    Collections.sort(activeThreads);
    return activeThreads;
  }



  private void onTestFinished(ITestResult tr)
  {
    // Clear when a test finishes instead before the next one starts
    // so that we get the output generated by any @BeforeClass method
    // etc.
    addTestResult(tr);
  }



  private void outputTestProgress(Object finishedTestObject)
  {
    if (doProgressNone)
    {
      return;
    }

    printStatusHeaderOnce();

    if (doProgressTime)
    {
      long curTimeMs = System.currentTimeMillis();
      long durationSec = (curTimeMs - startTimeMs) / 1000;
      long durationLastMs = curTimeMs - prevTimeMs;
      System.err.printf("{%2d:%02d (%3.0fs)}  ", (durationSec / 60),
          (durationSec % 60), (durationLastMs / 1000.0));
      prevTimeMs = curTimeMs;
    }

    if (doProgressTestCount)
    {
      System.err.printf("{%3dc %4dm %5di %df}  ", _classResults.size(),
          countTestMethods(), countTotalInvocations(),
          countTestsWithStatus(ITestResult.FAILURE));
    }

    if (doProgressMemory)
    {
      Runtime runtime = Runtime.getRuntime();
      long beforeGc = System.currentTimeMillis();
      int gcs = runGc();
      long gcDuration = System.currentTimeMillis() - beforeGc;

      long totalMemory = runtime.totalMemory();
      long freeMemory = runtime.freeMemory();
      long curMemInUse = totalMemory - freeMemory;
      long memDelta = curMemInUse - prevMemInUse;
      double perMegaByte = 1.0 / (1024.0 * 1024.0);

      maxMemInUse = Math.max(maxMemInUse, curMemInUse);

      System.err.printf("{%5.1fMB  %+5.1fMB}  ", curMemInUse
          * perMegaByte, memDelta * perMegaByte);

      if (doProgressMemoryGcs)
      {
        System.err.printf("{%2d gcs  %4.1fs}  ", gcs,
            gcDuration / 1000.0);
      }
      prevMemInUse = curMemInUse;
    }

    if (doProgressThreadCount)
    {
      System.err.printf("{#td %3d}  ", Thread.activeCount());
    }

    if (finishedTestObject == null)
    {
      System.err.println(": starting");
    }
    else
    {
      String abbrClass = packageLessClass(finishedTestObject);
      System.err.printf(": %s ", abbrClass).flush();
      System.err.println();
    }

    if (doProgressThreadChanges)
    {
      List<String> currentThreads = listAllThreadNames();
      List<String> newThreads = removeExactly(prevThreads,
          currentThreads);
      List<String> oldThreads = removeExactly(currentThreads,
          prevThreads);

      if (!newThreads.isEmpty())
      {
        System.err.println("  Thread changes:");
        for (int i = 0; i < oldThreads.size(); i++)
        {
          String threadName = oldThreads.get(i);
          System.err.println("    + " + threadName);
        }
        for (int i = 0; i < newThreads.size(); i++)
        {
          String threadName = newThreads.get(i);
          System.err.println("    - " + threadName);
        }
      }

      prevThreads = currentThreads;
    }
  }



  private String packageLessClass(Object obj)
  {
    return obj.getClass().getName().replaceAll(".*\\.", "");
  }



  private synchronized void printStatusHeaderOnce()
  {
    if (statusHeaderPrinted)
    {
      return;
    }
    statusHeaderPrinted = true;

    if (doProgressNone)
    {
      return;
    }

    System.err.println();
    System.err.println("How to read the progressive status info:");

    if (doProgressTime)
    {
      System.err
          .println("  Test duration status: {Total min:sec.  Since last status sec.}");
    }

    if (doProgressTestCount)
    {
      System.err
          .println("  Test count status:  {# test classes  # test methods  # test method invocations  # test failures}.");
    }

    if (doProgressMemory)
    {
      System.err
          .println("  Memory usage status: {MB in use  +/-change since last status}");
    }

    if (doProgressMemoryGcs)
    {
      System.err
          .println("  GCs during status:  {GCs done to settle used memory   time to do it}");
    }

    if (doProgressThreadCount)
    {
      System.err
          .println("  Thread count status:  {#td number of active threads}");
    }

    if (doProgressThreadChanges)
    {
      System.err
          .println("  Thread change status: +/- thread name for new or finished threads since last status");
    }

    System.err.println("  TestClass (the class that just completed)");
    System.err.println();
  }



  /**
   * Removes toRemove from base. If there are duplicate items in base,
   * then only one is removed for each item in toRemove.
   *
   * @return a new List with base with toRemove items removed from it
   */
  private List<String> removeExactly(List<String> base,
      List<String> toRemove)
  {
    List<String> diff = new ArrayList<String>(base);
    for (int i = 0; i < toRemove.size(); i++)
    {
      String item = toRemove.get(i);
      diff.remove(item);
    }
    return diff;
  }



  private int runGc()
  {
    Runtime runtime = Runtime.getRuntime();
    int numGcs;
    long curMem = usedMemory();
    long prevMem = Long.MAX_VALUE;
    StringBuilder gcConvergence = new StringBuilder();
    for (numGcs = 0; (prevMem > curMem) && numGcs < 100; numGcs++)
    {
      runtime.runFinalization();
      runtime.gc();
      Thread.yield();
      Thread.yield();

      prevMem = curMem;
      curMem = usedMemory();

      gcConvergence.append("[" + numGcs + "]: " + (prevMem - curMem))
          .append("  ");
    }
    return numGcs;
  }



  private long usedMemory()
  {
    Runtime runtime = Runtime.getRuntime();
    return runtime.totalMemory() - runtime.freeMemory();
  }



  private void writeAntTestsFailedMarker(String outputDirectory)
  {
    // Signal 'ant' that all of the tests passed by removing this
    // special file.
    if ((countTestsWithStatus(ITestResult.FAILURE) == 0)
        && (countTestsWithStatus(ITestResult.SKIP) == 0))
    {
      new File(outputDirectory, ANT_TESTS_FAILED_FILE_NAME).delete();
    }
  }



  private void writeReportToFile(File reportFile)
  {
    PrintStream reportStream = null;
    try
    {
      reportStream = new PrintStream(new FileOutputStream(reportFile));
    }
    catch (FileNotFoundException e)
    {
      System.err
          .println("Could not open "
              + reportFile
              + " for writing.  Will write the unit test report to the console instead.");
      e.printStackTrace(System.err);
      reportStream = System.err;
    }

    reportStream.println(center("UNIT TEST REPORT"));
    reportStream.println(center("----------------") + EOL);
    reportStream.println("Finished at: " + (new Date()));
    reportStream.println("# Test classes: " + _classResults.size());
    reportStream.println("# Test classes interleaved: "
        + _classesWithTestsRunInterleaved.size());
    reportStream.println("# Test methods: " + countTestMethods());
    reportStream.println("# Tests passed: "
        + countTestsWithStatus(ITestResult.SUCCESS));
    reportStream.println("# Tests failed: "
        + countTestsWithStatus(ITestResult.FAILURE));
    reportStream.println(EOL + DIVIDER_LINE + DIVIDER_LINE + EOL + EOL);
    reportStream.println(center("TEST CLASSES RUN INTERLEAVED"));
    reportStream.println(EOL + EOL);
    for (Iterator<Class<?>> iterator = _classesWithTestsRunInterleaved
        .iterator(); iterator.hasNext();)
    {
      Class<?> cls = iterator.next();
      reportStream.println("  " + cls.getName());
    }

    reportStream.println(EOL + DIVIDER_LINE + DIVIDER_LINE + EOL + EOL);
    reportStream.println(center("FAILED TESTS"));
    reportStream.println(EOL + EOL);
    reportStream.println(_bufferedTestFailures);

    reportStream.println(EOL + DIVIDER_LINE + DIVIDER_LINE + EOL);

    reportStream.println(getTimingInfo());

    reportStream.close();

    if ((countTestsWithStatus(ITestResult.FAILURE) == 0)
        && (countTestsWithStatus(ITestResult.SKIP) != 0))
    {
      System.err
          .println("There were no explicit test failures, but some tests were skipped (possibly due to errors in @Before* or @After* methods).");
      System.exit(-1);
    }
  }



  private void writeReportToScreen(File reportFile)
  {
    // HACK: print out status for the last test object
    outputTestProgress(_lastTestObject);

    List<ITestResult> failedTests = getFailedTests();
    StringBuilder failed = new StringBuilder();
    for (int i = 0; i < failedTests.size(); i++)
    {
      ITestResult failedTest = failedTests.get(i);
      String fqMethod = getFqMethod(failedTest);
      int numFailures = 1;
      // Peek ahead to see if we had multiple failures for the same
      // method
      // In which case, we list it once with a count of the failures.
      while (((i + 1) < failedTests.size())
          && fqMethod.equals(getFqMethod(failedTests.get(i + 1))))
      {
        numFailures++;
        i++;
      }

      failed.append("  ").append(fqMethod);

      if (numFailures > 1)
      {
        failed.append(" (x " + numFailures + ")");
      }

      failed.append(EOL);
    }

    if (failed.length() > 0)
    {
      System.err.println("The following unit tests failed: ");
      System.err.println(failed);
      System.err.println();
      System.err
          .println("Include the ant option '-Dtest.failures=true' to rerun only the failed tests.");
    }
    else
    {
      System.err.println("All of the tests passed.");
    }

    System.err.println();
    System.err.println("Wrote full test report to:");
    System.err.println(reportFile.getAbsolutePath());
    System.err.println("Test classes run interleaved: "
        + _classesWithTestsRunInterleaved.size());

    // Try to hard to reclaim as much memory as possible.
    runGc();

    System.err.printf("Final amount of memory in use: %.1f MB",
        (usedMemory() / (1024.0 * 1024.0))).println();
    if (doProgressMemory)
    {
      System.err.printf("Maximum amount of memory in use: %.1f MB",
          (maxMemInUse / (1024.0 * 1024.0))).println();
    }
    System.err.println("Final number of threads: "
        + Thread.activeCount());

    System.err.println();

    if (doProgressThreadChanges)
    {
      System.err.print(threadStacksToString());
    }

    if (_classesWithTestsRunInterleaved.size() > 0)
    {
      System.err
          .println("WARNING:  Some of the test methods for multiple classes "
              + "were run out of order (i.e. interleaved with other classes).  Either "
              + "a class doesn't have the sequential=true annotation, which should "
              + "have been reported already or there has been a regression with TestNG.");
    }
  }



  synchronized StringBuilder getTimingInfo()
  {
    StringBuilder timingOutput = new StringBuilder();
    timingOutput.append(center("TESTS RUN BY CLASS")).append(EOL);
    timingOutput.append(
        center("[method-name total-time (total-invocations)]")).append(
        EOL + EOL);
    for (TestClassResults results : _classResults.values())
    {
      results.getTimingInfo(timingOutput);
    }

    timingOutput.append(EOL + DIVIDER_LINE + DIVIDER_LINE + EOL);

    getSlowestTestsOutput(timingOutput);
    return timingOutput;
  }
}
