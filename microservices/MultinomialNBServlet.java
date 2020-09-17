package servlet;

import com.jmoss.AppMgr;
import com.jmoss.Constants;
import com.jmoss.IxClientCallbacks;
import com.jmoss.IxThinClientCallbacks;
import com.jmoss.data.*;
import com.jmoss.logic.AxPrediction;
import com.jmoss.logic.UxLogic;
import com.jmoss.math.Equation;
import com.jmoss.plugins.weka.MLPrediction;
import com.jmoss.plugins.weka.MLResult;
import com.jmoss.plugins.weka.NaiveBayesMultinomialText;
import com.jmoss.util.Properties;
import com.jmoss.util.*;
import weka.classifiers.AbstractClassifier;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.SerializationHelper;
import weka.core.converters.CSVLoader;
import weka.core.stopwords.Rainbow;

import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

import static com.jmoss.util.Java.getStackTrace;
import static com.jmoss.util.Utils.readDelimited;

@WebServlet(
  name = "MultinomialNBServlet",
  urlPatterns = {"/multinomialNB"}
)
public class MultinomialNBServlet extends HttpServlet implements IxThinClientCallbacks {

  private final MultiKeyMap<String,String,AbstractClassifier> classifiers = new MultiKeyMap<>("Classifiers");
  private final MultiKeyMap<String,String,Instances> trainers = new MultiKeyMap<>("Trainers");

  private Map<String, String> results = new Hashtable<>();
  private Map<String, CommonMutableTreeNode> substrings;
  private HashBagX<String, List, String> mappings;
  private CommonMutableTreeNode hier;

  private String heuristicsAttribute;
  private int xmxGB = 5;

  private boolean lowercaseTokens = true;
  private boolean normalize = false;

  private static final A9n<String,String> wekaconvert_train = new A9n<>("\"\"", "--");
  private static final A9n<String,String> wekaconvert_pred = new A9n<>("--", "\"");

  private static final String app_name = MultinomialNBServlet.class.getSimpleName();
  private static final String app_folder = new File("").getAbsolutePath();

  private static final String PROP_COMPANY = MultinomialNBServlet.class.getSimpleName() + "." + "company";
  private static final String PROP_COPYRIGHT = MultinomialNBServlet.class.getSimpleName() + "." + "copyright";
  private static final String PROP_FAMILY = MultinomialNBServlet.class.getSimpleName() + "." + "family";
  private static final String PROP_XMX_GB = MultinomialNBServlet.class.getSimpleName() + "." + "XMX_GB";

  private static final String PROP_HEURISTICS = MultinomialNBServlet.class.getSimpleName() + "." + "heuristics";

  private static final String DELIMITER = Net.encodeURL(Constants.kSymbolBoxDoubleHorizontal);

  /**
   **/
  private File wekafy(final File raw) throws Exception {
    final List<List<String>> rows = readDelimited(raw, false, false, ",", "//", false);
    final List<List<String>> new_rows = new ArrayList<>(rows.size());
    for(List<String> row : rows) {
      final List<String> new_row = new ArrayList<>(row.size());
      for(String field : row) {
        final String new_field = field.replace(wekaconvert_train.a1(), wekaconvert_train.a2());
        new_row.add(new_field);
      }

      new_rows.add(new_row);
    }

    String newPath = String.format("weka.%s", raw.getName());
    Utils.writeDelimited(newPath, new_rows, ",", false);
    File newFile = new File(newPath);
    System.out.printf("%s: Created Weka Training Data: %s%n", getClass().getSimpleName(), newFile.getAbsolutePath());
    return newFile;
  }

  /**
   **/
  private AbstractClassifier getClassifier(final String method, final String mlClass, final String modelFile) throws ClassNotFoundException, InstantiationException, IllegalAccessException, InvocationTargetException {
    AbstractClassifier classifier = classifiers.get(method, modelFile);
    if(classifier == null) {
      String modelPath = DAO.kData + File.separator + DAO.kModels + File.separator + method + File.separator + modelFile;
      try {
        classifier = (AbstractClassifier) SerializationHelper.read(modelPath);
        System.out.printf("getClassifier: Read model %s from %s%n", classifier.getClass().getSimpleName(), modelPath);
        if(classifier instanceof NaiveBayesMultinomialText) {
          NaiveBayesMultinomialText nc = (NaiveBayesMultinomialText) classifier;
          System.out.println(nc.dump(0));
        }
      }
      catch(Exception e) {
        long heap = Java.getMaximumHeapSize();
        if(heap > 5L * Constants.k1GB) {
          System.out.printf("getClassifier: Need to build a %s model for %s ...%n", mlClass, modelFile);
        }
        else {
          System.out.printf("getClassifier: Need to build a %s model for %s which will require Heap larger than 5GB, e.g. -Xmx6144M or -Xmx6G%n", mlClass, modelFile);
          throw new RuntimeException("Retry with a larger Heap size");
        }
      }
      finally {
        if(classifier == null) {
          Class<?> c = Class.forName(method);
          Constructor<?>[] constructors = c.getConstructors();
          for(int i = 0; i < constructors.length; i++) {
            Constructor<?> ctor = constructors[i];
            Class<?>[] pTypes = ctor.getParameterTypes();
            if(pTypes.length == 0) {
              classifier = (AbstractClassifier) ctor.newInstance();
              break;
            }
          }
        }
      }

      classifiers.put(method, modelFile, classifier);
    }

    return classifier;
  }

  /**
   **/
  private Instances getTrainer(final String method, final String name, final String trainingPath, final int classIndex, final String stringAtts) throws Exception {
    Instances trainer = trainers.get(method, name);
    if(trainer == null) {
      CSVLoader trainingLoader = new CSVLoader();
      final File rawFile = new File(trainingPath);
      final File wekaFile = wekafy(rawFile);

      trainingLoader.setStringAttributes(stringAtts); // e.g. "1,2" Only works for MultinomialNB? E.g. it needs String attribute feature but a Nominal attribute class
      trainingLoader.setSource(wekaFile);
      trainer = trainingLoader.getDataSet();
      trainer.setClassIndex(classIndex);
      trainers.put(method, name, trainer);
    }

    return trainer;
  }

  /**
   **/
  private void loadHeuristics() {
    heuristicsAttribute = Preferences.getProps().getProperty(PROP_HEURISTICS, "");

    try {
      substrings = (Map<String, CommonMutableTreeNode>) SerializationHelper.read(DAO.kSerialized + File.separator + "com.jmoss.plugins.weka.NaiveBayesMultinomialText" + File.separator + "substrings.ser");
      if(substrings != null) {
        System.out.printf("%s: Loaded %d substrings%n", getClass().getSimpleName(), substrings.size());
      }
      else {
        System.out.printf("%s: No substrings, using Stemmer%n", getClass().getSimpleName());
      }
    }
    catch(Exception e) {
      e.printStackTrace();
      System.out.printf("%s: Could not load substrings, using Stemmer%n", getClass().getSimpleName());
    }

    try {
      mappings = (HashBagX<String, List, String>) SerializationHelper.read(DAO.kSerialized + File.separator + "com.jmoss.plugins.weka.NaiveBayesMultinomialText" + File.separator + "mappings.ser");
      if(mappings != null) {
        System.out.printf("%s: Loaded %d mappings%n", getClass().getSimpleName(), mappings.size());
      }
      else {
        System.out.printf("%s: No mappings%n", getClass().getSimpleName());
      }
    }
    catch(Exception e) {
      e.printStackTrace();
      System.out.printf("%s: Could not load mappings%n", getClass().getSimpleName());
    }

    try {
      hier = (CommonMutableTreeNode) SerializationHelper.read(DAO.kSerialized + File.separator + "com.jmoss.plugins.weka.NaiveBayesMultinomialText" + File.separator + "hier.ser");
      if(hier != null) {
        System.out.printf("%s: Loaded hier (%d nodes)%n", getClass().getSimpleName(), hier.getDescendantCount(Integer.MAX_VALUE));
      }
      else {
        System.out.printf("%s: No hier%n", getClass().getSimpleName());
      }
    }
    catch(Exception e) {
      e.printStackTrace();
      System.out.printf("%s: Could not load hier%n", getClass().getSimpleName());
    }
  }

  /**
   * Allows distinct handling of features
   */
  private List<MLPrediction> multinomialNBbyFeature(final CSVLoader testLoader, final String mlClass, final File trainingFile, final String stringAtts, final boolean probOfClassAsWord, final StringBuilder buf) throws Exception {
    List<MLPrediction> p9s = new ArrayList<>();
    String timestamp = Utils.formatNow();
    System.out.printf("%n%s %s.multinomialNBbyFeature(...,mlClass=%s,...,stringAtts=%s,probOfClassAsWord=%b)%n", timestamp, getClass().getSimpleName(), mlClass, stringAtts, probOfClassAsWord);

    init((IxClientCallbacks) this);
    loadHeuristics();

    Instances test = testLoader.getDataSet();
    test.setClassIndex(test.numAttributes() - 1);

    String trainingPath = DAO.kData + File.separator + trainingFile.getName();
    Instances trainer = getTrainer("com.jmoss.plugins.weka.NaiveBayesMultinomialText", trainingFile.getName(), trainingPath, test.numAttributes() - 1, stringAtts);
    System.out.printf("%n%s %s.multinomialNBbyFeature: Trainer: %s%n", Utils.formatNow(), getClass().getSimpleName(), trainer.toSummaryString());

    Attribute classAttribute = trainer.attribute(test.numAttributes() - 1);
    Enumeration<Object> en = classAttribute.enumerateValues();
    List<Object> classValues = Utils.toList(en);

    NaiveBayesMultinomialText classifier = (NaiveBayesMultinomialText) getClassifier("com.jmoss.plugins.weka.NaiveBayesMultinomialText", mlClass, Utils.changeExtension(trainingFile.getName(), "model"));

    Enumeration<Attribute> attributes = test.enumerateAttributes();
    while(attributes.hasMoreElements()) {
      Attribute attribute = attributes.nextElement();
      System.out.printf("%n%s %s: Test Attribute: %s", Utils.formatNow(), getClass().getSimpleName(), attribute.name());
      if(attribute.name().equals(heuristicsAttribute)) {
        classifier.addTokenizer(trainer, attribute, probOfClassAsWord, true, 2, "com.jmoss.plugins.weka.NormalizingTokenizer");
        classifier.setHier(attribute, hier); // Hier does not extend Serializable, so set it no matter what
        classifier.setMappings(attribute, mappings); // Mappings does not extend Serializable, so set it no matter what
        classifier.setSubstrings(attribute, substrings); // Substrings does not extend Serializable, so set it no matter what
      }
      else {
        classifier.addTokenizer(trainer, attribute, probOfClassAsWord, false, 3, "com.jmoss.plugins.weka.CompoundTokenizer");
        classifier.setSubstrings(attribute, substrings); // Substrings does not extend Serializable, so set it no matter what
      }
    }

    System.out.printf("%n%s %s: Class Attribute: %s%n", Utils.formatNow(), getClass().getSimpleName(), classAttribute.name());
    if(classAttribute.name().equals(heuristicsAttribute)) {
      classifier.addTokenizer(trainer, classAttribute, probOfClassAsWord, true, 2, "com.jmoss.plugins.weka.NormalizingTokenizer");
      classifier.setMappings(classAttribute, mappings); // Mappings does not extend Serializable, so set it no matter what
      classifier.setHier(classAttribute, hier); // Hier does not extend Serializable, so set it no matter what
      classifier.setSubstrings(classAttribute, substrings); // Substrings does not extend Serializable, so set it no matter what
    }
    else {
      classifier.addTokenizer(trainer, classAttribute, probOfClassAsWord, false, 3, "com.jmoss.plugins.weka.CompoundTokenizer");
      classifier.setSubstrings(classAttribute, substrings); // Substrings does not extend Serializable, so set it no matter what
    }

    classifier.setStopwordsHandler(new Rainbow()); // StopWordsHandler does not extend Serializable, so set it no matter what    
    if(classifier.hasModel() == false) {
      classifier.setLowercaseTokens(lowercaseTokens);
      classifier.setNormalizeDocLength(normalize);
      for(int i = 0; i < test.numAttributes() - 1; i++) {
        classifier.buildClassifier(trainer, test.attribute(i));
      }

      String path = DAO.kData + File.separator + DAO.kModels + File.separator + "com.jmoss.plugins.weka.NaiveBayesMultinomialText";
      Utils.verifyPath(path);
      String modelPath = path + File.separator + Utils.changeExtension(trainingFile.getName(), "model");
      SerializationHelper.write(modelPath, classifier);

      Utils.writeFile(path + File.separator + Utils.changeExtension(trainingFile.getName(), "txt"), timestamp + Utils.DOUBLESPACE + classifier.toString(), false);
      //Utils.writeFile(path + File.separator + Utils.changeExtension(dataset.trainingFile.getName(), "classify"), timestamp + Utils.DOUBLESPACE + dbuf.toString(), false);
    }

    buf.append(String.format("%s=%s", "Timestamp", timestamp));
    buf.append(DELIMITER);
    buf.append(Utils.DOUBLESPACE);
    buf.append(String.format("%s=%s", "Summary", trainer.toSummaryString()));
    buf.append(DELIMITER);
    buf.append(Utils.DOUBLESPACE);

    int ni = test.numInstances();
    System.out.printf("%n%s %s: test.numInstances(): %d", Utils.formatNow(), getClass().getSimpleName(), ni);
    for(int i = 0; i < ni; i++) {
      Instance iTest = test.instance(i);
      Instance iCopy = (Instance) iTest.copy();

      List<MLResult> topN = classifier.classifyInstance(iCopy, test.attribute(0), 3);
      System.out.printf("%n%s %s: Classifier returned %d results%n", Utils.formatNow(), getClass().getSimpleName(), topN.size());
      for(MLResult r : topN) {
        int idx = (int) r.getIdx();
        String weka_p8n = classValues.get(idx).toString();
        LinkedHashMap<String, Count> myInputVector = classifier.getInputVectors(test.attribute(0)).get(weka_p8n);
        String p8n = weka_p8n.replace(wekaconvert_pred.a1(), wekaconvert_pred.a2());

        int classIndex = iTest.classIndex();
        StringBuilder input = new StringBuilder();
        for(int k = 0; k < classIndex; k++) {
          input.append(Text.dequote(iTest.toString(k), true));
          input.append(Constants.kSymbolBoxDoubleVertical);
        }

        if(input.length() > Constants.kSymbolBoxDoubleVertical.length()) {
          input.setLength(input.length() - Constants.kSymbolBoxDoubleVertical.length());
        }

        for(int j = 0; j < test.numAttributes() - 1; j++) {
          System.out.printf("%n%s %s: Test Attribute[%d]: %s", Utils.formatNow(), getClass().getSimpleName(), j, test.attribute(j).name());
          MLResult byp8n = classifier.classifyInstance(iCopy, test.attribute(j), p8n);
          int pop1 = trainer.classAttribute().numValues();
          int pop2 = trainer.attribute(j).numValues();
          double calcProbability = UxLogic.calcProbability(pop1, pop2, r.getVal(), byp8n.getVal());
          Equation eq = new Equation((a, b) -> String.format("(%.2f -- %.4f %s %.2f): ", r.getVal(), byp8n.getVal(), Constants.kSymbolAlmostEqual, calcProbability) + (a + b), calcProbability, 0.0);

          MLPrediction theP8n = new MLPrediction(trainer, classifier, input.toString(), new LinkedHashMap<>(classifier.getInputVector(test.attribute(0))), Double.isNaN(r.getIdx()) ? "Error: Missing Value" : p8n, myInputVector, -1, calcProbability, eq);
          theP8n.setByHeader(MLPrediction.kInHeader, DB.escapeSQL(theP8n.getByHeader(MLPrediction.kInHeader, 0).toString(), DAO.kUnknown, 0x2));
          theP8n.setByHeader(MLPrediction.kOutHeader, DB.escapeSQL(theP8n.getByHeader(MLPrediction.kOutHeader, 0).toString(), DAO.kUnknown, 0x2));
          theP8n.setByHeader(MLPrediction.kOptionHeader, String.format("probOfClassAsWord: %b", probOfClassAsWord));
          p9s.add(theP8n);
        }
      }
    }

    Collections.sort(p9s);
    Collections.reverse(p9s);
    int rank = 1;
    for(MLPrediction p8n : p9s) {
      p8n.setRank(rank++);
      buf.append(Utils.stringize(p8n.toNVs(MLPrediction.allHeaderList, 0), DELIMITER, ""));
      buf.append(DELIMITER);
      buf.append(Utils.NL);
    }

    String path = DAO.kData + File.separator + DAO.kModels + File.separator + "com.jmoss.plugins.weka.NaiveBayesMultinomialText";
    Utils.verifyPath(path);
    Utils.writeFile(path + File.separator + Utils.changeExtension(trainingFile.getName(), "out"), buf.toString(), false);

    return p9s;
  }

  /**
   **/
  private void updateClassifier(final CSVLoader testLoader, final File trainingFile, final String stringAtts, final boolean probOfClassAsWord, final int operation) throws Exception {
    Instances test = testLoader.getDataSet();
    test.setClassIndex(test.numAttributes() - 1);
    String mlClass = test.classAttribute().toString();
    String modelFile = Utils.changeExtension(trainingFile.getName(), "model");
    if(operation == Constants.kCreate) {
      List<String> values = new ArrayList<>();
      for(int i = 0; i < test.firstInstance().numAttributes(); i++) {
        values.add(Utils.escapeDelimited(test.firstInstance().stringValue(i), ","));
      }

      Utils.writeDelimited(trainingFile.getAbsolutePath(), Collections.singletonList(values), ",", true);
      classifiers.remove("com.jmoss.plugins.weka.NaiveBayesMultinomialText", modelFile);

      String modelPath = AppMgr.getApplicationFolder() + File.separator + DAO.kData + File.separator + DAO.kModels + File.separator + "com.jmoss.plugins.weka.NaiveBayesMultinomialText" + File.separator + modelFile;
      new File(modelPath).delete();

      StringBuilder buf = new StringBuilder();
      multinomialNBbyFeature(testLoader, mlClass, trainingFile, stringAtts, probOfClassAsWord, buf);
    }
    else if(operation == Constants.kDelete) {
      NaiveBayesMultinomialText classifier = (NaiveBayesMultinomialText) getClassifier("com.jmoss.plugins.weka.NaiveBayesMultinomialText", mlClass, modelFile);
      Attribute classAttribute = test.attribute(test.numAttributes() - 1);
      List<List<String>> rows = classifier.remove(test.firstInstance().stringValue(test.classIndex()), classAttribute);
      Utils.writeDelimited(trainingFile.getAbsolutePath(), rows, ",", true);
      classifiers.remove("com.jmoss.plugins.weka.NaiveBayesMultinomialText", modelFile);

      String modelPath = AppMgr.getApplicationFolder() + File.separator + DAO.kData + File.separator + DAO.kModels + File.separator + "com.jmoss.plugins.weka.NaiveBayesMultinomialText" + File.separator + modelFile;
      new File(modelPath).delete();

      StringBuilder buf = new StringBuilder();
      multinomialNBbyFeature(testLoader, mlClass, trainingFile, stringAtts, probOfClassAsWord, buf);
    }
  }

  /**
   * http://127.0.0.1:8082/multinomialNB?Symbol=piece&Descriptions=A+piece+performed+after+a+play%2C+usually+a+farce+or%C2%B6+++other+small+entertainment.&Contexts=Miscellaneous&Classes=Thing
   * http://127.0.0.1:8082/multinomialNB?Symbol=piece&Descriptions=A+piece+performed+after+a+play%2C+usually+a+farce+or%C2%B6+++other+small+entertainment.&Classes=Thing&Contexts=Miscellaneous
   * @param req
   * @param resp
   * @throws IOException
   */
  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    try {
      if(AppMgr.isInitialized(getClass().getName()) == false) {
        AppMgr.init(getClass().getName(), "multinomialNB");
      }
    }
    catch(ClassNotFoundException|IllegalAccessException|InstantiationException e) {
      String s = getStackTrace(e, false);
      System.out.println(s);
    }

    System.out.printf("%n********* Begin Session *********%n");
    String pString = req.getQueryString();
    StringBuilder buf = new StringBuilder();
    try {
      int minlevel = 2;
      CSVLoader testLoader = new CSVLoader();
      int classIndex = -1;

      if(pString != null) {
        pString = Net.decodeURL(pString);
        System.out.printf("%n%s %s: The query string is: %s%n", Utils.formatNow(), getClass().getSimpleName(), pString);

        List<String> nvs = Text.tokenize(pString, Net.DELIMITER, pString);
        List<String> headers = new ArrayList<>();

        // Headers
        for(String nv : nvs) {
          List<String> tokens = Text.tokenize(nv, NameValuePair.DELIMITER, nv);
          if(tokens.size() == 2) {
            if(tokens.get(0).equals(AppMgr.getSharedResString(Constants.kOperationLabel))) {
              // This is a setting, doesn't need a header
            }
            else if(tokens.get(0).equals(AxPrediction.kLevelAttribute)) {
              // This is a setting, doesn't need a header
            }
            else if(tokens.get(0).equals("Symbol") == false) { // Symbol is a no-op for now
              headers.add(tokens.get(0));
              System.out.printf("%s %s: Found header %s%n", Utils.formatNow(), getClass().getSimpleName(), tokens.get(0));
            }
          }
        }

        int operation = -1;
        int features = headers.size() - 1;
        String mlClass = headers.get(features);

        StringBuilder csv = new StringBuilder();
        csv.append(Utils.stringize(headers, ",", ""));
        csv.append(Utils.NL);

        // Test Instances
        for(int i = 0; i < nvs.size(); i++) {
          String nv = nvs.get(i);
          List<String> tokens = Text.tokenize(nv, NameValuePair.DELIMITER, nv);
          if(tokens.size() == 2) {
            if(tokens.get(0).equals(AppMgr.getSharedResString(Constants.kOperationLabel))) {
              operation = AppMgr.getOperation(tokens.get(1));
            }
            else if(tokens.get(0).equals(AxPrediction.kLevelAttribute)) {
              try {
                minlevel = Integer.valueOf(tokens.get(1));
                System.out.printf("%s: Set minlevel to %d%n", getClass().getSimpleName(), minlevel);
              }
              catch(NumberFormatException e) {
                e.printStackTrace();
              }
            }
            else if(tokens.get(0).equals("Symbol") == false) { // Symbol is a no-op for now
              csv.append(Utils.escapeDelimited(tokens.get(1), ","));
              csv.append(',');
              classIndex++;
            }
          }
        }

        csv.setLength(csv.length()-1);
        csv.append(Utils.NL);

        System.out.printf("%n%s %s: The CSV is:%n%s%n", Utils.formatNow(), getClass().getSimpleName(), csv);
        final String weka = csv.toString().replace(wekaconvert_train.a1(), wekaconvert_train.a2());
        System.out.printf("%s %s: The Weka is:%n%s%n", Utils.formatNow(), getClass().getSimpleName(), weka);

        String stringAtts = Utils.stringize(Utils.numbered(1, features), "1");
        List<MLPrediction> all = new ArrayList<>();
        StringBuilder filespec = new StringBuilder();
        filespec.append('(');
        for(int i = 0; i < features; i++) {
          filespec.append(Text.numeronize(headers.get(i)));
        }

        filespec.append(")-to-");
        filespec.append(headers.get(features));
        String basename = filespec.toString();
        System.out.printf("%s %s: The Basename is %s%n", Utils.formatNow(), getClass().getSimpleName(), basename);

        String trainingPath = new File(DAO.kData).getAbsolutePath();
        System.out.printf("%s %s: The Training Path is %s%n", Utils.formatNow(), getClass().getSimpleName(), trainingPath);

        List<Integer> levels = new ArrayList<>();
        {
          String pattern = String.format("*%s*\\(*\\)*\\.csv", Utils.escapeRegex(Text.leftOfFromEnd(basename, "-", 1, basename))); // e.g. (D10sC6s)-to-Classes-Raw(02).csv
          System.out.printf("%s %s: The file matching pattern is %s%n", Utils.formatNow(), getClass().getSimpleName(), pattern);
          List<String> matches = new ArrayList<>();
          Utils.find(trainingPath, pattern, 3, false, matches);
          for(String match : matches) {
            String s = Text.eparameterize(Utils.baseName(match));
            levels.add(Integer.valueOf(s));
          }
        }

        if(operation == Constants.kCreate || operation == Constants.kDelete) {
          if(levels.size() > 0) {
            Collections.sort(levels);
            int start = Math.max(levels.get(0), minlevel);
            for(int level = start; level <= levels.get(levels.size() - 1); level++) {
              List<String> matches = new ArrayList<>();
              String pattern = String.format("*%s*\\(%02d\\)*\\.csv", Utils.escapeRegex(Text.leftOfFromEnd(basename, "-", 1, basename)), level); // e.g. (D10sC6s)-to-Classes-Raw(02).csv
              testLoader.setSource(new ByteArrayInputStream(weka.getBytes()));
              testLoader.setStringAttributes(stringAtts); // only needed for MultinomialNB?
              Utils.find(trainingPath, pattern, 3, false, matches);
              for(String match : matches) {
                File trainingFile = new File(match);
                updateClassifier(testLoader, trainingFile, stringAtts, false, operation);
              }
            }
          }
          else {
            buf.append("No ML model files present in the form (features)-to-class(level).csv");
            System.out.println(buf);
          }
        }
        else if(operation == Constants.kRead) {
          if(levels.size() > 0) {
            String result = results.get(pString);
            if(result != null) {
              System.out.printf("Found result in cache for %s%n", pString);
              buf.setLength(0);
              buf.append(result);
            }
            else {
              Collections.sort(levels);

              Vector<Vector<String>> rows = new Vector<>();
              Set<String> summaries = new HashSet<>();
              int start = Math.max(levels.get(0), minlevel);
              for(int level = start; level <= levels.get(levels.size() - 1); level++) {
                List<String> matches = new ArrayList<>();
                String pattern = String.format("*%s*\\(%02d\\)*\\.csv", Utils.escapeRegex(Text.leftOfFromEnd(basename, "-", 1, basename)), level); // e.g. (D10sC6s)-to-Classes-Raw(02).csv

                testLoader.setSource(new ByteArrayInputStream(weka.getBytes()));
                testLoader.setStringAttributes(stringAtts); // only needed for MultinomialNB?
                Utils.find(trainingPath, pattern, 3, false, matches);
                for(String match : matches) {
                  System.out.printf("%n%s %s: Found match: %s%n", Utils.formatNow(), getClass().getSimpleName(), match);

                  File trainingFile = new File(match);
                  {
                    boolean probOfClassAsWord = false;
                    List<MLPrediction> predictions = multinomialNBbyFeature(testLoader, mlClass, trainingFile, stringAtts, probOfClassAsWord, buf);
                    all.addAll(predictions);
                  }
                  {
                    boolean probOfClassAsWord = true;
                    List<MLPrediction> predictions = multinomialNBbyFeature(testLoader, mlClass, trainingFile, stringAtts, probOfClassAsWord, buf);
                    all.addAll(predictions);
                  }
                }
              }

              results.put(pString, buf.toString());

              for(MLPrediction prediction : all) {
                rows.add(new Vector<>(prediction.toRow(MLPrediction.allHeaderList, 0)));
                summaries.add(prediction.getTrainer().toSummaryString());
              }
            }
          }
          else {
            buf.append("No ML model files present in the form (features)-to-class(level).csv");
            System.out.println(buf);
          }
        }
        else {
          buf.append(String.format("Unknown operation: %d (valid values: %d, %d, %d)", operation, Constants.kCreate, Constants.kRead, Constants.kDelete));
          System.out.println(buf);
        }
      }
    }
    catch(Exception e) {
      String s = getStackTrace(e, false);
      buf.setLength(0);
      buf.append(s);
      System.out.println();
      System.out.println(s);
    }

    ServletOutputStream out = resp.getOutputStream();
    System.out.printf("%s: Results: %s%n", getClass().getSimpleName(), buf);
    String s = buf.toString();
    s = s.replaceAll(Constants.kSymbolAlmostEqual, DB.escapeSQL(Constants.kSymbolAlmostEqual, DAO.kUnknown, 0x2));
    out.write(s.getBytes());
    out.flush();
    out.close();

    System.out.printf("%n********* End   Session *********%n");
  }

  @Override
  public String getErrorPage() {
    return null;
  }

  @Override
  public String getInfoPage() {
    return null;
  }

  @Override
  public String getHomePage() {
    return null;
  }

  @Override
  public String getCurrentPage() {
    return getServletContext().getContextPath();
  }

  @Override
  public void init(IxClientCallbacks ixClientCallbacks) {
    xmxGB = Integer.valueOf(Preferences.getProps().getProperty(PROP_XMX_GB, String.valueOf(xmxGB)));
  }

  @Override
  public void feedbackAudible(String s) {

  }

  @Override
  public ResourceUtilities getPrivateResourceUtilities() {
    return null;
  }

  @Override
  public ResourceBundle getPrivateResources() {
    return null;
  }

  @Override
  public Preferences getPreferences() {
    return Preferences.getInstance();
  }

  @Override
  public Properties getAppProperties() {
    return Preferences.getProps();
  }

  @Override
  public String createApplicationFolder(String s) {
    return null;
  }

  @Override
  public String getApplicationFolder() {
    return app_folder;
  }

  @Override
  public CommandManager getCommandManager() {
    return AppMgr.getCommandManager();
  }

  @Override
  public UserManager getUserManager() {
    return AppMgr.getUserManager();
  }

  @Override
  public void logError(IxLogClient client, String s) {
    AppMgr.logError(client, s);
  }

  @Override
  public void logEvent(IxLogClient client, String s) {
    AppMgr.logEvent(client, s);
  }

  @Override
  public void logDebug(IxLogClient client, String s) {
    LogMgr.logDebug(client, s);
  }

  @Override
  public void logDebug(IxLogClient client, Throwable throwable) {
    LogMgr.logDebug(client, throwable);
  }

  @Override
  public void logTrace(IxLogClient client, Throwable throwable) {
    AppMgr.logTrace(client, throwable, true);
  }

  @Override
  public void logDump(IxLogClient client) {
    AppMgr.logDump(client);
  }

  @Override
  public void logList(String s) {

  }

  @Override
  public String getTempPath() {
    return app_folder;
  }

  @Override
  public Integer getMajorRevision() {
    return 1;
  }

  @Override
  public Integer getMinorRevision() {
    return 0;
  }

  @Override
  public Integer getBugRevision() {
    return 0;
  }

  @Override
  public Integer getMicroRevision() {
    return 0;
  }

  @Override
  public void setBuildType(String s) {

  }

  @Override
  public Integer getBuildType() {
    return 1;
  }

  @Override
  public Integer getClientType() {
    return 1;
  }

  @Override
  public Integer getTargetType(String s) {
    return 1;
  }

  @Override
  public String getBuildStamp() {
    return "Test";
  }

  @Override
  public String getProductName() {
    return app_name;
  }

  @Override
  public String getProductFamilyName() {
    return AppMgr.getProperty(Constants.kPreference, PROP_FAMILY, getClass().getName());
  }

  @Override
  public String getCompanyName() {
    return AppMgr.getProperty(Constants.kPreference, PROP_COMPANY, getClass().getName());
  }

  @Override
  public String getCopyright() {
    return AppMgr.getProperty(Constants.kPreference, PROP_COPYRIGHT, getClass().getName());
  }
}