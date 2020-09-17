/*
 * @(#)DictServicePlugIn.java	02/21/2011
 *
 * Copyright 2001-2013 Jeffrey Moss All Rights Reserved.
 *
 */

package com.jmoss.plugins;

import com.aonaware.services.webservices.ArrayOfDictionaryWord;
import com.aonaware.services.webservices.Definition;
import com.aonaware.services.webservices.DictService;
import com.aonaware.services.webservices.DictServiceHttpGet;
import com.aonaware.services.webservices.DictServiceSoap;
import com.aonaware.services.webservices.DictionaryWord;
import com.aonaware.services.webservices.WordDefinition;

import com.jmoss.AppMgr;
import com.jmoss.AxFullCallback;
import com.jmoss.Constants;
import com.jmoss.data.ConnectionManager;
import com.jmoss.data.LoginUser;
import com.jmoss.data.NameValuePair;
import com.jmoss.data.ServerDetails;
import com.jmoss.kb.IxIdentifier;
import com.jmoss.kb.kbCategory;
import com.jmoss.kb.kbContext;
import com.jmoss.kb.kbMgr;
import com.jmoss.kb.kbObject;
import com.jmoss.ui.UIFactory;
import com.jmoss.util.ErrorManager;
import com.jmoss.util.FeedbackManager;
import com.jmoss.util.IxErrorClient;
import com.jmoss.util.IxFormatter;
import com.jmoss.util.IxLogClient;
import com.jmoss.util.JSON;
import com.jmoss.util.Java;
import com.jmoss.util.LogMgr;
import com.jmoss.util.Net;
import com.jmoss.util.Properties;
import com.jmoss.util.StartsWithComparator;
import com.jmoss.util.Text;
import com.jmoss.util.Utils;

import java.io.File;
import java.io.IOException;

import java.net.MalformedURLException;
import java.util.*;

import javax.swing.JMenuItem;

import javax.xml.namespace.QName;
import javax.xml.ws.WebServiceException;

import org.json.JSONObject;

/**
 *
 * @author  Jeffrey Moss
 */
public final class DictServicePlugIn extends AxPlugIn implements IxLogClient, IxErrorClient {

  private Map<String,ServerDetails> services = new HashMap<>();
  private String username = "";
  private int timeout = 1000;
  private boolean wsIsUp = true;
  private MyCallBack cb = null;

  // Access from threads
  private DictServicePlugIn instance;

  private static final QName SERVICE_NAME = new QName("http://services.aonaware.com/webservices/", "DictService");
  private static final String PROP_HOSTS = "hosts";
  private static final String PROP_CLIENT = DictServicePlugIn.class.getSimpleName() + "." + "Client";
  private static final String PROP_TIMEOUT = DictServicePlugIn.class.getSimpleName() + "." + "TIMEOUT";
  private static final String DELIMITER = Net.encodeURL(Constants.kSymbolBoxDoubleHorizontal);

  /**
   * @param args
   */
  public static void main(String... args) {
    DictServicePlugIn dsp = new DictServicePlugIn();
    dsp.execute();
  }

  /**
   **/
  public DictServicePlugIn() {
    instance = this;
    
    theMenuItem = new JMenuItem("Dictionary Lookup Service");
    name = "Dictionary Lookup Service Client";
    description = "Look up definitions from various online dictionaries";
    
    // Mark the menus as plugin-related
    initializeMenus();
  }

  /**
   * @return
   */
  public boolean supportLookup() {
    ServerDetails theServer = services.get("wordnet/definition");
    if(theServer != null) {
      String url = String.format("%s?%s=%s&%s", theServer.getAddress(), AppMgr.getSharedResString(Constants.kOperationLabel), AppMgr.getSharedResString(Constants.kOperationRead), "test");
      return Net.pingUrl(url, 5000);
    }
    else {
      return false;
    }
  }

  /**
   **/
  public void initialize() {
    cb = new MyCallBack();
    cb.init();

    Properties props = cb.getAppProperties();
    username = props.getProperty(PROP_CLIENT, username);
    timeout = Integer.valueOf(props.getProperty(PROP_TIMEOUT, String.valueOf(timeout)));

    ErrorManager.setSyncPoint(name);

    String hosts = props.getProperty(PROP_HOSTS, "");
    List<String> tokens = Text.tokenize(hosts, IxFormatter.kGroupDelimiterStr, "");

    try {
      ServerDetails theServer = getServerDetails(tokens, "wordnet/definition");
      services.put("wordnet/definition", theServer);
    }
    catch(MalformedURLException e) {
      ErrorManager.queue(name, this, e.getMessage());
    }

    try {
      ServerDetails theServer = getServerDetails(tokens, "wordnet/category");
      services.put("wordnet/category", theServer);
    }
    catch(MalformedURLException e) {
      ErrorManager.queue(name, this, e.getMessage());
    }

    try {
      ServerDetails theServer = getServerDetails(tokens, "wordnet/holonym");
      services.put("wordnet/holonym", theServer);
    }
    catch(MalformedURLException e) {
      ErrorManager.queue(name, this, e.getMessage());
    }

    try {
      ServerDetails theServer = getServerDetails(tokens, "wordnet/hypernym");
      services.put("wordnet/hypernym", theServer);
    }
    catch(MalformedURLException e) {
      ErrorManager.queue(name, this, e.getMessage());
    }

    try {
      ServerDetails theServer = getServerDetails(tokens, "wordnet/hyponym");
      services.put("wordnet/hyponym", theServer);
    }
    catch(MalformedURLException e) {
      ErrorManager.queue(name, this, e.getMessage());
    }

    try {
      ServerDetails theServer = getServerDetails(tokens, "wordnet/member");
      services.put("wordnet/member", theServer);
    }
    catch(MalformedURLException e) {
      ErrorManager.queue(name, this, e.getMessage());
    }

    try {
      ServerDetails theServer = getServerDetails(tokens, "wordnet/meronym");
      services.put("wordnet/meronym", theServer);
    }
    catch(MalformedURLException e) {
      ErrorManager.queue(name, this, e.getMessage());
    }

    if(ErrorManager.hasSyncErrors(name)) {
      ErrorManager.showQueued(this, this, null, "", name, true);
    }

    ErrorManager.clearSyncPoint(name);
  }

  /**
   **/
  private ServerDetails getServerDetails(final List<String> hosts, final String service) throws MalformedURLException {
    LoginUser user = AppMgr.getUserManager().getLoginByComponent(service);
    if(user != null) {
      return AppMgr.getServiceManager().add(name, hosts, user.getServer().getComponent(), user.getServer().getPort());
    }
    else {
      return null;
    }
  }

  /**
   **/
  public void execute() {
    LogMgr.putTrace(getClass(), "execute");

    if(ConnectionManager.isUp()) {
      if(false) {
        DictService theService = new DictService(DictService.WSDL_LOCATION, SERVICE_NAME);

        try {
          DictServiceSoap port = theService.getDictServiceSoap();
          AppMgr.logEvent(instance, "Calling " + port.toString());

          String text = UIFactory.getText(null, name, "Enter text to define", "My text", this, name);
          if(text != null) {
            List definitions = getDescriptions(text);
            UIFactory.showList(null, name, "Definitions of " + text, definitions, true, name);
            LogMgr.popTrace("execute");
          }
        }
        catch(WebServiceException e) {
          FeedbackManager.showInfo(name + " - DictServiceSoap", true);

          try {
            DictServiceSoap port = theService.getDictServiceSoap12();
            AppMgr.logEvent(instance, "Calling " + port.toString());

            String text = UIFactory.getText(null, name, "Enter text to define", "My text", this, name);
            if(text != null) {
              List definitions = getDescriptions(text);
              UIFactory.showList(null, name, "Definitions of " + text, definitions, true, name);
              LogMgr.popTrace("execute");
            }
          }
          catch(WebServiceException ee) {
            FeedbackManager.showInfo(name + " - DictServiceSoap12", true);
            throw e;
          }
        }
      }
      else {
        String text = UIFactory.getText(null, name, "Enter text to define", "MyText", this, name);
        List<String> descriptions = getDescriptions(text);
        UIFactory.showList(null, name, text, descriptions, true, name);
      }
    }
    else {
      FeedbackManager.showInfo(name + " is unavailable - no connection", true);
      LogMgr.popTrace("execute");
    }
  }
  
  /**
   * Set up=false for 30 seconds
   */
  private void wsIsDown() {
    wsIsUp = false;

    new Thread() {
      public void run() {
        try {
          yield();
          sleep(30000);
          wsIsUp = true;
        }
        catch(InterruptedException ie) {
          ie.printStackTrace();
        }
      }
    }.start();
  }
  
  /**
   * @param text
   * @return
   */
  public List<String> getDescriptions(final String text) {
    LogMgr.putTrace(getClass(), "getDescriptions");

    List<String> definitions = new ArrayList<>();
    if(wsIsUp && ConnectionManager.isUp()) {
      DictService theService = null;
      try {
        theService = new DictService(DictService.WSDL_LOCATION, SERVICE_NAME);
        DictServiceHttpGet dictServiceHttpGet = theService.getDictServiceHttpGet();
        AppMgr.logEvent(instance, AppMgr.getLogMgr().mkstr("DictServiceHttpGet {0}", dictServiceHttpGet.toString()));

        ArrayOfDictionaryWord arrayOfDictionaryWord = dictServiceHttpGet.match(text, "");
        List<DictionaryWord> dictionaryWord = arrayOfDictionaryWord.getDictionaryWord();
        for(DictionaryWord dictionaryWord1 : dictionaryWord) {
          String s = dictionaryWord1.getWord();
          definitions.add(s);
        }
      }
      catch(Exception e) {
        ErrorManager.addError(this, Java.getStackTrace(e, false));
        wsIsDown();

        if(theService != null) {
          try {
            // The Port Type defines the supported operations for the service, e.g., Define, Match, DefineInDict
            DictServiceSoap portType = theService.getDictServiceSoap();
            AppMgr.logEvent(instance, AppMgr.getLogMgr().mkstr("Instantiated Port Type {0}", portType.toString()));

            WordDefinition wd = portType.define(text);
            List<Definition> defs = wd.getDefinitions().getDefinition();
            for(Definition def : defs) {
              String s = def.getWordDefinition();
              definitions.add(s);
            }
          }
          catch(Exception x) {
            return getDescriptions(text);
          }
        }
        else {
          definitions = getDefinitions(text);
        }
      }
    }
    else {
      AppMgr.logEvent(instance, "Web Service and/or Connection Manager is not up");
      definitions = getDefinitions(text);
    }

    LogMgr.popTrace("getDescriptions");
    return definitions;
  }

  private List<String> getDefinitions(final String text) {
    List<String> definitions = new ArrayList<>();
    {
      LoginUser user = AppMgr.getUserManager().getLoginByName(this.username);
      if(user != null) {
        String server = user.getServer().getAddress().toString();
        int port = user.getServer().getPort();
        String comp = user.getServer().getComponent();
        String q = String.format("Symbol=%s", text);
        String url = String.format("%s:%d/%s?%s", server, port, comp, q);
        try {
          String resp = Net.readURL(url, timeout, this);
          if(resp != null) {
            List<String> list = Text.tokenize(resp, DELIMITER, resp);
            for(String item : list) {
              JSONObject json = JSON.create(item);
              json.put(NameValuePair.UNIVERSE, this.username);
              json.put(NameValuePair.TRACE, url);
              definitions.add(json.toString());
            }
          }
        }
        catch(Exception x) {
          ErrorManager.addError(this, Java.getStackTrace(x, Constants.NL_SUBSTITUTE, 2, false));
        }
      }
    }

    {
      ServerDetails theServer = services.get("wordnet/definition");
      if(theServer != null) {
        String q = String.format("Symbol=%s", text);
        String url = String.format("%s?%s", theServer.getAddress(), q);
        try {
          String resp = Net.readURL(url, timeout, this);
          if(resp != null) {
            List<String> tokens = tokens = Text.tokenize(resp, DELIMITER, resp);
            for(String token : tokens) {
              JSONObject json = JSON.create(token);
              json.put(NameValuePair.UNIVERSE, "wordnet/definition");
              json.put(NameValuePair.TRACE, url);
              definitions.add(json.toString());
            }
          }
        }
        catch(Exception x) {
          ErrorManager.addError(this, Java.getStackTrace(x, Constants.NL_SUBSTITUTE, 2, false));
        }
      }
    }

    return definitions;
  }

  /**
   * @param text
   * @return
   */
  public List<kbCategory> getCategories(final String text) {
    Set<kbCategory> out = new HashSet<>();


    ServerDetails theServer = services.get("wordnet/category");
    if(theServer != null) {
      List<String> tokens = Text.tokenize(text, Net.DELIMITER, text);
      String nv = Utils.find(tokens, IxIdentifier.kSymbol, StartsWithComparator.getInstance(false, true));
      String url = String.format("%s?%s", theServer.getAddress(), Text.rightOf(nv, NameValuePair.DELIMITER, 1, nv));
      try {
        String resp = Net.readURL(url, timeout, this);
        if(resp != null) {
          tokens = Text.tokenize(resp, DELIMITER, resp);
          for(String token : tokens) {
            if(JSON.isJSON(token)) {
              JSONObject json = JSON.create(token);
              int rank = json.getInt(AppMgr.getSharedResString(Constants.kNumberLabel));
              String category = json.getString(AppMgr.getSharedResString(Constants.kNameLabel));
              String definition = json.getString(AppMgr.getSharedResString(Constants.kDefinitionLabel));
              kbCategory aCategory = kbMgr.getCategoryManager().findCategoryBySymbol(category, true);
              if(aCategory != null) {
                //aCategory.addNameValue(NameValuePair.EXPOSITION, category.equalsIgnoreCase(aCategory.getSymbol()) ? definition : category + IxFormatter.kDefDelimiter + definition, true);
                //aCategory.addNameValue(NameValuePair.UNIVERSE, theServer.getDescription(), true);
                //aCategory.addNameValue(AppMgr.getSharedResString(Constants.kNumberLabel), String.valueOf(rank), true);
                out.add(aCategory);
              }
            }
          }
        }
      }
      catch(Exception x) {
        ErrorManager.addError(this, Java.getStackTrace(x, Constants.NL_SUBSTITUTE, 2, false));
      }
    }

    return new ArrayList<>(out);
  }

  /**
   * @param text
   * @return
   */
  public List<kbCategory> getMembers(final String text) {
    Set<kbCategory> out = new HashSet<>();

    ServerDetails theServer = services.get("wordnet/member");
    if(theServer != null) {
      List<String> tokens = Text.tokenize(text, Net.DELIMITER, text);
      String nv = Utils.find(tokens, IxIdentifier.kSymbol, StartsWithComparator.getInstance(false, true));
      String url = String.format("%s?%s", theServer.getAddress(), Text.rightOf(nv, NameValuePair.DELIMITER, 1, nv));
      try {
        String resp = Net.readURL(url, timeout, this);
        if(resp != null) {
          tokens = Text.tokenize(resp, DELIMITER, resp);
          for(String token : tokens) {
            if(JSON.isJSON(token)) {
              JSONObject json = JSON.create(token);
              int rank = json.getInt(AppMgr.getSharedResString(Constants.kNumberLabel));
              String member = json.getString(AppMgr.getSharedResString(Constants.kNameLabel));
              String definition = json.getString(AppMgr.getSharedResString(Constants.kDefinitionLabel));
              kbCategory category = kbMgr.getCategoryManager().findCategoryBySymbol(member, true);
              if(category != null) {
                //aCategory.addNameValue(NameValuePair.EXPOSITION, category.equalsIgnoreCase(aCategory.getSymbol()) ? definition : category + IxFormatter.kDefDelimiter + definition, true);
                //aCategory.addNameValue(NameValuePair.UNIVERSE, theServer.getDescription(), true);
                //aCategory.addNameValue(AppMgr.getSharedResString(Constants.kNumberLabel), String.valueOf(rank), true);
                out.add(category);
              }
            }
          }
        }
      }
      catch(Exception x) {
        ErrorManager.addError(this, Java.getStackTrace(x, Constants.NL_SUBSTITUTE, 2, false));
      }
    }

    return new ArrayList<>(out);
  }

  /**
   * @param text
   * @return
   */
  public List<kbObject> getClasses(final String text) {
    Set<kbObject> out = new HashSet<>();

    ServerDetails theServer = services.get("wordnet/hypernym");
    if(theServer != null) {
      List<String> tokens = Text.tokenize(text, Net.DELIMITER, text);
      String nv = Utils.find(tokens, IxIdentifier.kSymbol, StartsWithComparator.getInstance(false, true));
      String url = String.format("%s?%s", theServer.getAddress(), Text.rightOf(nv, NameValuePair.DELIMITER, 1, nv));
      try {
        String resp = Net.readURL(url, timeout, this);
        if(resp != null) {
          tokens = Text.tokenize(resp, DELIMITER, resp);
          for(String token : tokens) {
            if(JSON.isJSON(token)) {
              JSONObject json = JSON.create(token);
              int rank = json.getInt(AppMgr.getSharedResString(Constants.kNumberLabel));
              String hypernym = json.getString(AppMgr.getSharedResString(Constants.kNameLabel));
              String definition = json.getString(AppMgr.getSharedResString(Constants.kDefinitionLabel));
              kbObject theObject = kbMgr.getObjectManager().findObjectByIdentifierInContexts(hypernym, kbContext.kEmptyContextList, false, false);
              if(theObject != null) {
                theObject.load(false, kbObject.MetaData.kAll, false);
                theObject.addNameValue(NameValuePair.EXPOSITION, hypernym.equalsIgnoreCase(theObject.getSymbol()) ? definition : hypernym + IxFormatter.kDefDelimiter + definition, true);
                theObject.addNameValue(NameValuePair.TRACE, url, true);
                theObject.addNameValue(NameValuePair.UNIVERSE, "wordnet/hypernym", true);
                theObject.addNameValue(AppMgr.getSharedResString(Constants.kNumberLabel), String.valueOf(rank), true);
                out.add(theObject);
              }
            }
          }
        }
      }
      catch(Exception x) {
        ErrorManager.addError(this, Java.getStackTrace(x, Constants.NL_SUBSTITUTE, 2, false));
      }
    }

    return new ArrayList<>(out);
  }

  /**
   * @param text
   * @return
   */
  public List<kbObject> getTypes(final String text) {
    Set<kbObject> out = new HashSet<>();

    ServerDetails theServer = services.get("wordnet/hyponym");
    if(theServer != null) {
      List<String> tokens = Text.tokenize(text, Net.DELIMITER, text);
      String nv = Utils.find(tokens, IxIdentifier.kSymbol, StartsWithComparator.getInstance(false, true));
      String url = String.format("%s?%s", theServer.getAddress(), Text.rightOf(nv, NameValuePair.DELIMITER, 1, nv));
      try {
        String resp = Net.readURL(url, timeout, this);
        if(resp != null) {
          tokens = Text.tokenize(resp, DELIMITER, resp);
          for(String token : tokens) {
            if(JSON.isJSON(token)) {
              JSONObject json = JSON.create(token);
              int rank = json.getInt(AppMgr.getSharedResString(Constants.kNumberLabel));
              String hyponym = json.getString(AppMgr.getSharedResString(Constants.kNameLabel));
              String definition = json.getString(AppMgr.getSharedResString(Constants.kDefinitionLabel));
              kbObject theObject = kbMgr.getObjectManager().findObjectByIdentifierInContexts(hyponym, kbContext.kEmptyContextList, false, false);
              if(theObject != null) {
                theObject.load(false, kbObject.MetaData.kAll, false);
                theObject.addNameValue(NameValuePair.EXPOSITION, hyponym.equalsIgnoreCase(theObject.getSymbol()) ? definition : hyponym + IxFormatter.kDefDelimiter + definition, true);
                theObject.addNameValue(NameValuePair.TRACE, url, true);
                theObject.addNameValue(NameValuePair.UNIVERSE, "wordnet/hyponym", true);
                theObject.addNameValue(AppMgr.getSharedResString(Constants.kNumberLabel), String.valueOf(rank), true);
                out.add(theObject);
              }
            }
          }
        }
      }
      catch(Exception x) {
        ErrorManager.addError(this, Java.getStackTrace(x, Constants.NL_SUBSTITUTE, 2, false));
      }
    }

    return new ArrayList<>(out);
  }

  /**
   * @param text
   * @return
   */
  public List<kbObject> getAggregates(final String text) {
    Set<kbObject> out = new HashSet<>();

    ServerDetails theServer = services.get("wordnet/holonym");
    if(theServer != null) {
      List<String> tokens = Text.tokenize(text, Net.DELIMITER, text);
      String nv = Utils.find(tokens, IxIdentifier.kSymbol, StartsWithComparator.getInstance(false, true));
      String url = String.format("%s?%s", theServer.getAddress(), Text.rightOf(nv, NameValuePair.DELIMITER, 1, nv));
      try {
        String resp = Net.readURL(url, timeout, this);
        if(resp != null) {
          tokens = Text.tokenize(resp, DELIMITER, resp);
          for(String token : tokens) {
            if(JSON.isJSON(token)) {
              JSONObject json = JSON.create(token);
              int rank = json.getInt(AppMgr.getSharedResString(Constants.kNumberLabel));
              String holonym = json.getString(AppMgr.getSharedResString(Constants.kNameLabel));
              String definition = json.getString(AppMgr.getSharedResString(Constants.kDefinitionLabel));
              kbObject theObject = kbMgr.getObjectManager().findObjectByIdentifierInContexts(holonym, kbContext.kEmptyContextList, false, false);
              if(theObject != null) {
                theObject.load(false, kbObject.MetaData.kAll, false);
                theObject.addNameValue(NameValuePair.EXPOSITION, holonym.equalsIgnoreCase(theObject.getSymbol()) ? definition : holonym + IxFormatter.kDefDelimiter + definition, true);
                theObject.addNameValue(NameValuePair.TRACE, url, true);
                theObject.addNameValue(NameValuePair.UNIVERSE, "wordnet/holonym", true);
                theObject.addNameValue(AppMgr.getSharedResString(Constants.kNumberLabel), String.valueOf(rank), true);
                out.add(theObject);
              }
            }
          }
        }
      }
      catch(Exception x) {
        ErrorManager.addError(this, Java.getStackTrace(x, Constants.NL_SUBSTITUTE, 2, false));
      }
    }

    return new ArrayList<>(out);
  }

  /**
   * @param text
   * @param arg
   * @return
   */
  public List<kbObject> getParts(final String text, final String arg) {
    Set<kbObject> out = new HashSet<>();

    ServerDetails theServer = services.get("wordnet/meronym");
    if(theServer != null) {
      List<String> tokens = Text.tokenize(text, Net.DELIMITER, text);
      String nv = Utils.find(tokens, IxIdentifier.kSymbol, StartsWithComparator.getInstance(false, true));
      String url = String.format("%s?%s", theServer.getAddress(), Text.rightOf(nv, NameValuePair.DELIMITER, 1, nv));
      try {
        String resp = Net.readURL(url, timeout, this);
        if(resp != null) {
          tokens = Text.tokenize(resp, DELIMITER, resp);
          for(String token : tokens) {
            if(JSON.isJSON(token)) {
              JSONObject json = JSON.create(token);
              int rank = json.getInt(AppMgr.getSharedResString(Constants.kNumberLabel));
              String meronym = json.getString(AppMgr.getSharedResString(Constants.kNameLabel));
              String definition = json.getString(AppMgr.getSharedResString(Constants.kDefinitionLabel));
              kbObject theObject = kbMgr.getObjectManager().findObjectByIdentifierInContexts(meronym, kbContext.kEmptyContextList, false, false);
              if(theObject != null) {
                theObject.load(false, kbObject.MetaData.kAll, false);
                theObject.addNameValue(NameValuePair.EXPOSITION, meronym.equalsIgnoreCase(theObject.getSymbol()) ? definition : meronym + IxFormatter.kDefDelimiter + definition, true);
                theObject.addNameValue(NameValuePair.TRACE, url, true);
                theObject.addNameValue(NameValuePair.UNIVERSE, "wordnet/meronym", true);
                theObject.addNameValue(AppMgr.getSharedResString(Constants.kNumberLabel), String.valueOf(rank), true);
                out.add(theObject);
              }
            }
          }
        }
      }
      catch(Exception x) {
        ErrorManager.addError(this, Java.getStackTrace(x, Constants.NL_SUBSTITUTE, 2, false));
      }
    }

    return new ArrayList<>(out);
  }
  
  /**
   * IxTableRow
   * @param header
   * @param mask
   * @return
   */
  @Override
  public Object getByHeader(String header, int mask) {
    if(header.equals(kHostHeader)) {
      if(this.host != null) {
        kbObject hostObject = kbMgr.getObjectManager().fetchObject(this.host.toString());
        if(hostObject != null) {
          return hostObject.symbol();
        }
        else {
          return this.host;
        }
      }
      else {
        return kNoHost;
      }
    }
    else {
      return super.getByHeader(header, mask);
    }
  }

  /**
   * ICommand
   * @return 
   */
  public boolean undoable() {
    return false;
  }
  
  /**
   * IxLogClient
   * Every log entry written by this object starts with a certain prefix, after timestamp, id, and thread tags.
   * @return 
   */
  public String logEntryPrefix() {
    return name;
  }

  /**
   * IxLogClient
   * Dump the contents of the object to a newline-delimited string.
   */
  public String dump(int verbose) {
    return name;
  }

  /**
   * IxErrorClient
   * Every error entry written by this object starts with a certain prefix, after timestamp, id, and thread tags.
   * @return 
   */
  public String errorEntryPrefix() {
    return name;
  }
  /**
   **/
  private final class MyCallBack extends AxFullCallback {    
    private String appName = DictServicePlugIn.class.getSimpleName();
    private String appFolder = new File("").getAbsolutePath();
    private String productName = null;
    private String productFamilyName = null;
    private Properties props = new Properties(true);

    /**
     **/
    public MyCallBack() {
    }

    @Override
    public Properties getAppProperties() {
      return props;
    }

    /**
     **/
    public void init() {
      props.load(this, DictServicePlugIn.this);
    }

    /**
     * AxCallback
     * IxFullClientCallbacks
     * @return
     */
    @Override
    public String getProductName() {
      return productName == null ? appName : productName;
    }

    /**
     **/
    private void setProductName(final String productName) {
      this.productName = productName;
    }

    /**
     * AxCallback
     * IxFullClientCallbacks
     * @return
     */
    @Override
    public String getProductFamilyName() {
      return productFamilyName == null ? super.getProductFamilyName() : productFamilyName;
    }

    /**
     **/
    private void setProductFamilyName(final String productFamilyName) {
      this.productFamilyName = productFamilyName;
    }

    @Override
    public String getApplicationFolder() {
      return Utils.kUserHome + File.separator + AppMgr.getDataSourceFileBranch() + File.separator + AppMgr.getSharedResString(Constants.kConfigFolder) + File.separator + AppMgr.getSharedResString(Constants.kPlugInsFolder);
    }

    /**
     **/
    public void logError(String msg) {
      LogMgr.logError(msg);
    }

    /**
     **/
    public void logEvent(String msg) {
      LogMgr.logEvent(msg);
    }

    /**
     **/
    public void logDebug(String msg) {
      LogMgr.logDebug(msg);
    }
    
    /**
     * IxFullClientCallbacks
     * Log a debug event
     * @param client
     * @param ex
     */
    public void logDebug(IxLogClient client, Throwable ex) {
      LogMgr.logDebug(client, ex);
    }

    /**
     **/
    public void logTrace(Throwable ex) {
      LogMgr.logTrace(ex, false);
    }
    
    /**
     **/
    public void logList(String list) {
      LogMgr.logList(list);
    }

    /**
    **/
    public void logError(IxLogClient client, String msg) {
      LogMgr.logError(client, msg);
    }

    /**
     **/
    public void logEvent(IxLogClient client, String msg) {
      LogMgr.logEvent(client, msg);
    }
    
    /**
     **/
    public void logDebug(IxLogClient client, String msg) {
      LogMgr.logEvent(client, msg);
    }

    /**
     **/
    public void logTrace(IxLogClient client, Throwable ex) {
      LogMgr.logTrace(client, ex, false);
    }

    /**
     **/
    public void logDump(IxLogClient client) {
      LogMgr.logDump(client);
    }
  }
}
