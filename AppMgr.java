/*
 * @(#)AppMgr.java	 10/31/2001
 *
 * Copyright 1995-2020 Jeffrey Moss All Rights Reserved.
 *
 */

package com.jmoss;

import com.jmoss.data.*;
import com.jmoss.plugins.IxPlugInListener;
import com.jmoss.ui.IxController;
import com.jmoss.ui.MainController;
import com.jmoss.ui.P10nMgr;
import com.jmoss.ui.UIFactory;
import com.jmoss.util.ActionManager;
import com.jmoss.util.CommandManager;
import com.jmoss.util.DocumentationManager;
import com.jmoss.util.ErrorManager;
import com.jmoss.util.ErrorModel;
import com.jmoss.util.InstrumentationManager;
import com.jmoss.util.IxErrorClient;
import com.jmoss.util.IxLogClient;
import com.jmoss.util.Java;
import com.jmoss.util.LogMgr;
import com.jmoss.util.Preferences;
import com.jmoss.util.ResourceUtilities;
import com.jmoss.util.SearchManager;
import com.jmoss.util.Utils;
import com.jmoss.util.ValidationManager;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Random;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.Vector;

/**
 * Central manager for application that provides access to various application-wide accessor methods.
 *
 * Manages the creation, registration, and accessibility of the Manager users required by a particular application.
 *
 * As a hub for the application's Managers, AppMr can provide the infrastructure for assigning DAO's to those Managers that
 * require persistence.
 *
 * May be used as a Logging delegate for classes that choose not to implement any logging interface.
 *
 * This class is independent of the type of application (Thick Client, Thin Client, J2EE) implementing it
 * and provides a model for the application class that should be defined
 * to manage application-level accessor methods.The application class can
 * be named something appropriate for the requirements while AppMgr will always have the same table.
 */
public final class AppMgr implements IxErrorClient {
  
  // This should point to the Application Class
  private static IxClientCallbacks application;
  
  // "Slots" for manager references
  // TODO: Need a Manager Factory so we don't need hard-coded slots?
  //       - but the names of the managers need to be represented somewhere - maybe a config file?
  //       Is there any value in moving these slots to a Factory object?
  private static LogMgr logManager;
  private static PlugInManager plugInManager;
  private static AddressBookManager addressBookManager;
  private static CommandManager commandManager;
  private static ConnectionManager connectionManager;
  private static FavoritesManager bookmarksManager;
  private static UserManager userManager;
  private static ContentManager contentManager;
  private static ReportManager reportManager;
  private static SearchManager searchManager;
  private static ServiceManager serviceManager;
  private static IxController mainController;

  /** This maps Manager names to their Objects */
  private static Map<String,IxManager> managers = new HashMap<>();

  /** This maps Application Component Names to the DAO types they use */
  private static Map<String,String> daoTypes;
  
  /** This maps Data Source Names to the DAO Factories they use */
  private static Map<String,DAOFactory> daoFactories;
  
  /** Default DAO for entire application */
  private static DAO defaultDAO;
  
  /** Create a unique ID for this session using the server instance, which is "0" until multiple deployments are supported */
  private static final String kSessionId = "0" + "@" + Utils.key();
  
  private static String  dataSourceFileBranch;
  private static String  dataSourceUrlBranch;
  
  /** Support for property change events */
  private static PropertyChangeSupport changeSupport;
  
  private static final List<PropertyChangeListener> listenerList = new Vector<>();
    
  /** Support for application-independent resources */
  // TODO: ResourceUtilities is Swing-dependent
  private static final ResourceUtilities ru = new ResourceUtilities();

  /** Resource Bundle Support */
  private static final ResourceBundle   res = new Resources();
  
  /** Dummy instance for callbacks */
  private static final AppMgr instance = new AppMgr();

  /**
   **/
  private AppMgr() { 
  }
  
  /**
   * IxClientCallbacks
   * This is how the plugged in app class exposes itself to the package.
   * Sets up resources, logging, etc.
   * This method is Idempotent.
   * @param client
   * @param name
   */
  public static void init(final IxClientCallbacks client, final String name) {
    if(application == null) {
      application = client;
    }
    else {
      throw new UnsupportedOperationException(String.format("AppMgr.init already set for (%s) %s (%s) will be ignored", application, Constants.NL_SUBSTITUTE, client));
    }

    ru.setResourcePackageName("com.jmoss.resources");

    try {
      load(instance);
    }
    catch(final IOException e) {
      logTrace(instance, e, false);
      ErrorManager.queue("init", instance, Java.getMessage(e));
    }

    initUI();

    if(logManager == null) {
      logManager = new LogMgr(name);
    }
  }

  /**
   **/
  public static void initUI() {
    // Set the application's table row height to the configured font plus 1/3 for top and bottom margins
    UIFactory.put("Table.rowHeight", (int) Math.round(Preferences.getFont().getSize() * 1.33));

    // Set the application's tree row height to the configured font plus 1/3 for top and bottom margins
    UIFactory.put("Tree.rowHeight", (int) Math.round(Preferences.getFont().getSize() * 1.33));

    UIFactory.put("Button.font", Preferences.getFont());

    UIFactory.put("CheckBox.font", Preferences.getFont());
    UIFactory.put("CheckBoxMenuItem.acceleratorFont", Preferences.getFont());
    UIFactory.put("CheckBoxMenuItem.font", Preferences.getFont());
    UIFactory.put("ComboBox.font", Preferences.getFont());

    UIFactory.put("EditorPane.font", Preferences.getFont());
    UIFactory.put("FileChooser.listFont", Preferences.getFont());
    UIFactory.put("Frame.font", Preferences.getFont());
    UIFactory.put("InternalFrame.titleFont", Preferences.getFont());
    UIFactory.put("List.font", Preferences.getFont());
    UIFactory.put("Panel.font", Preferences.getFont());
    UIFactory.put("TabbedPane.font", Preferences.getFont());
    UIFactory.put("TitledBorder.font", Preferences.getFont());
    UIFactory.put("ToggleButton.font", Preferences.getFont());
    UIFactory.put("ToolBar.font", Preferences.getFont());
    UIFactory.put("ToolTip.font", Preferences.getFont());
    UIFactory.put("Tree.font", Preferences.getFont());
    UIFactory.put("ViewPort.font", Preferences.getFont());

    UIFactory.put("Menu.acceleratorFont", Preferences.getFont());
    UIFactory.put("Menu.font", Preferences.getFont());
    UIFactory.put("MenuBar.font", Preferences.getFont());
    UIFactory.put("MenuItem.acceleratorFont", Preferences.getFont());
    UIFactory.put("MenuItem.font", Preferences.getFont());

    UIFactory.put("OptionPane.buttonFont", Preferences.getFont());
    UIFactory.put("OptionPane.font", Preferences.getFont());
    UIFactory.put("OptionPane.messageFont", Preferences.getFont());

    UIFactory.put("PopupMenu.font", Preferences.getFont());
    UIFactory.put("ProgressBar.font", Preferences.getFont());

    UIFactory.put("RadioButton.font", Preferences.getFont());
    UIFactory.put("RadioButtonMenuItem.acceleratorFont", Preferences.getFont());
    UIFactory.put("RadioButtonMenuItem.font", Preferences.getFont());
  }

  /**
   * Set application by specifying the class name.
   * @param className The class name of the application
   * @param name
   * @throws ClassNotFoundException
   * @throws IllegalAccessException
   * @throws InstantiationException
   */
  public static void init(final String className, final String name) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
    if(application == null) {
      application = (IxClientCallbacks) Class.forName(className).newInstance();
    }
    else {
      throw new InstantiationException("Already instantiated");
    }
    
    ru.setResourcePackageName("com.jmoss.resources");

    try {
      load(instance);
    }
    catch(final IOException e) {
      logTrace(instance, e, false);
      ErrorManager.queue("init", instance, Java.getMessage(e));
    }
    
    if(logManager == null) {
      logManager = new LogMgr(name);
    }
  }

  /**
   * @param className The class name of the application
   * @return
   */
  public static boolean isInitialized(final String className) {
    if(application == null) {
      return false;
    }
    else if(application.getClass().getName().equals(className)) {
      return true;
    }
    else {
      return false;
    }
  }

  /**
   * Exposes the instance so logging can be delegated here
   * @return 
   */
  public static AppMgr getInstance() { 
    return instance; 
  }

  /**
   * @param name
   * @return
   */
  public static IxManager getManager(final String name) {
    return managers.get(name);    
  }

  /**
   * Manager retrieval
   * @return 
   */
  public static LogMgr getLogMgr() {
    if(logManager != null) {
      return logManager;
    }
    else {
      return null;
    }
  }

  /**
   * Accessor
   * @return 
   */
  public static boolean createPlugInManager(IxPlugInListener l) {
    if(plugInManager == null) {
      plugInManager = PlugInManager.getPlugInManager(l);

      managers.put(plugInManager.getName(), plugInManager);
      return true;
    }
    else {
      return false;
    }
  }

  /**
   * Accessor
   * @return 
   */
  public static PlugInManager getPlugInManager() {
    plugInManager = PlugInManager.getPlugInManager(null);

    managers.put(plugInManager.getName(), plugInManager);

    return plugInManager;
  }

  /**
   * Accessor
   * @return 
   */
  public static CommandManager getCommandManager() {
    if(commandManager == null) {
      commandManager = CommandManager.getCommandManager();
      
      managers.put(commandManager.getName(), commandManager);
    }

    return commandManager;
  }

  /**
   * Accessor
   * @return
   * @throws Exception
   */
  public static boolean createBookmarksManager() throws Exception {
    if(bookmarksManager == null) {
      bookmarksManager = FavoritesManager.getFavoritesManager();
      
      managers.put(bookmarksManager.getName(), bookmarksManager);
      return true;
    }
    else {
      return false;
    }
  }

  /**
   * Manager retrieval
   * @return 
   */
  public static FavoritesManager getBookmarksManager() {
    return bookmarksManager;
  }

  /**
   * Manager retrieval
   * @return 
   */
  public static UserManager getUserManager() {
    return userManager;
  }

  /**
   * Manager registration
   * Not defined in IxClientCallbacks - optional and non-essential
   * @return
   */
  public static void setUserManager(final UserManager mgr) {
    userManager = mgr;

    managers.put(userManager.getName(), userManager);
  }

  /**
   * Manager retrieval
   * Not in IxClientCallbacks - optional and non-essential
   * @return
   */
  public static AddressBookManager getAddressBookManager() {
    return addressBookManager;
  }

  /**
   * Manager registration
   * Not defined in IxClientCallbacks - optional and non-essential
   * @return
   */
  public static void setAddressBookManager(AddressBookManager mgr) {
    addressBookManager = mgr;

    managers.put(addressBookManager.getName(), addressBookManager);
  }

  /**
   * Accessor
   * @return NOT NULL
   */
  public static ConnectionManager getConnectionManager() {
    if(connectionManager == null) {
      connectionManager = ConnectionManager.getConnectionManager();

      managers.put(connectionManager.getName(), connectionManager);
    }
    
    return connectionManager;
  }

  /**
   * Manager registration
   * Not defined in IxClientCallbacks - optional and non-essential
   * @return
   */
  public static void setConnectionManager(ConnectionManager mgr) {
    connectionManager = mgr;

    managers.put(connectionManager.getName(), connectionManager);
  }
  
  /**
   * Accessor
   * @return
   * @throws Exception
   */
  public static boolean createContentManager() throws Exception {
    if(contentManager == null) {
      contentManager = ContentManager.getContentManager();

      managers.put(contentManager.getName(), contentManager);
      return true;
    }
    else {
      return false;
    }
  }

  /**
   * Manager retrieval
   * @return
   */
  public static ContentManager getContentManager() {
    return contentManager;
  }

  /**
   * @return
   */
  public static DocumentationManager getDocumentationManager() {
    return DocumentationManager.getDocumentationManager();
  }

  /**
   * Accessor
   * @param rf
   * @return
   * @throws IOException
   * @throws ClassNotFoundException
   * @throws IllegalAccessException
   * @throws InstantiationException
   * @throws Exception
   */
  public static boolean createReportManager(final ReportFactory rf) throws IOException, ClassNotFoundException, IllegalAccessException, InstantiationException, Exception {
    if(reportManager == null) {
      reportManager = ReportManager.getInstance(Constants.kConnectionReport, rf);

      managers.put(reportManager.getName(), reportManager);
      return true;
    }
    else {
      return false;
    }
  }

  /**
   * Manager retrieval
   * @return 
   */
  public static ReportManager getReportManager() {
    return reportManager;
  }

  /**
   * Accessor
   * @return NOT NULL
   */
  public static SearchManager getSearchManager() {
    if(searchManager == null) {

      try {
        searchManager = new SearchManager();
      }
      catch(final ClassNotFoundException e) {
        ErrorManager.queue(SearchManager.class.getName(), instance, "Interface or abstract class: " + Java.getMessage(e));
      }
      catch(final IllegalAccessException e) {
        ErrorManager.queue(SearchManager.class.getName(), instance, "Access denied: " + Java.getMessage(e));
      }
      catch(final InstantiationException e) {
        ErrorManager.queue(SearchManager.class.getName(), instance, "Unresolved external: " + Java.getMessage(e));
      }
      catch(final DAOException e) {
        ErrorManager.queue(SearchManager.class.getName(), instance, "Data access error: " + Java.getMessage(e));
      }
      
      managers.put(searchManager.getName(), searchManager);
    }
    
    return searchManager;
  }

  /**
   * Manager retrieval
   * @return
   */
  public static ServiceManager getServiceManager() {
    if(serviceManager == null) {
      serviceManager = new ServiceManager();
    }

    return serviceManager;
  }

  /**
   * Accessor
   * @return 
   */
  public static IxController getMainController() {
    if(mainController == null) {
      mainController = new MainController();
    }
    
    return mainController;
  }
  
  /**
   * @param client
   * @param type
   */
  public static Object registerDaoClient(final String client, final int type) {
    if(daoTypes == null) {
      daoTypes = new HashMap<>();
    }
    
    IxManager mgr = managers.get(client);
    if(mgr != null) {
      if(mgr.hasDAO()) {
        IxPersistentManager pmgr = (IxPersistentManager)mgr;
        if(pmgr.canChangeDAO()) {
          IxMutableManager mmgr = (IxMutableManager)pmgr;
          int oldtype = Integer.valueOf(daoTypes.get(client));
          mmgr.changeDAO(oldtype, type);
        }
      }
    }

    return daoTypes.put(client, String.valueOf(type));
  }
  
  /**
   * @param name
   * @param f
   * @return
   */
  public static Object registerDaoFactory(final String name, final DAOFactory f) {
    if(daoFactories == null) {
      daoFactories = new HashMap<>();
    }

    return daoFactories.put(name, f);
  }
  
  /**
   * @param name
   * @return
   */
  public static int getDaoType(final String name) {
    if(daoTypes == null) {
      return DAO.kTypeUnknown;
    }
    else {
      Object val = daoTypes.get(name);
      if(val != null) {
        return Integer.valueOf(val.toString()).intValue();
      }
      else {
        return DAO.kTypeUnknown;
      }
    }
  }

  /**
   * @param name
   * @return
   */
  public static DAOFactory getDaoFactory(final String name) {
    if(daoFactories == null) {
      return null;
    }
    else {
      return daoFactories.get(name);
    }
  }
  
  /**
   * @return
   */
  public static Set<String> getDaoClients() {
    if(daoTypes == null) {
      return Constants.kEmptyStringSetK;
    }
    else {
      return daoTypes.keySet();
    }
  }

  /**
   * @param sort
   * @return
   */
  public static List<IxMutableManager> getMutableManagers(final boolean sort) {
    List<IxMutableManager> mgrs = new ArrayList<>();
    for(IxManager mgr:managers.values()) {
      if(mgr instanceof IxMutableManager) {
        mgrs.add((IxMutableManager)mgr);
      }
    }
    
    if(sort) {
      Collections.sort(mgrs);
    }
    
    return mgrs;
  }

  /**
   * @param component
   * @return
   */
  public static String createDataSourceNamePrefs(final String component) {
    return String.format("%s %s %s", application.getProductName(), component, Preferences.kName);
  }

  /**
   * @param component
   * @return
   */
  public static String createDataSourceNameData(final String component) {
    return String.format("%s %s", application.getProductName(), component);
  }

  /**
   * @return
   */
  public static String getDataSourceFileBranch() {
    if(dataSourceFileBranch == null) {
      dataSourceFileBranch = application.getProductFamilyName() + File.separator + application.getProductName();
    }

    return dataSourceFileBranch;
  }

  /**
   * @return
   */
  public static String getDataSourceUrlBranch() {
    if(dataSourceUrlBranch == null) {
      dataSourceUrlBranch = application.getProductFamilyName() + "/" + application.getProductName();
    }

    return dataSourceUrlBranch;
  }
  
  /**
   * @param dao
   * @param key
   * @return
   */
  public static NameValuePair createNameValuePair(final DAO dao, final String key) {
    NameValuePair nvp = new NameValuePair(key, "", "");
    nvp.setDAO(dao.getName(), dao.getType());
    return nvp;
  }

  /**
   * @param op
   * @return
   */
  public static String getOperation(final int op) {
    switch(op) {
    case Constants.kRead:
      return getSharedResString(Constants.kOperationRead);
    case Constants.kUpdate:
      return getSharedResString(Constants.kOperationUpdate);
    case Constants.kCreate:
      return getSharedResString(Constants.kOperationCreate);
    case Constants.kDelete:
      return getSharedResString(Constants.kOperationDelete);
    default:
      return String.format("Unknown operation: %d (valid values: %d, %d, %d)", op, Constants.kCreate, Constants.kRead, Constants.kDelete);
    }
  }

  /**
   * @param op
   * @return
   */
  public static int getOperation(final String op) {
    if(op.equals(getSharedResString(Constants.kOperationRead)))
      return Constants.kRead;
    else if(op.equals(getSharedResString(Constants.kOperationUpdate)))
      return Constants.kUpdate;
    else if(op.equals(getSharedResString(Constants.kOperationCreate)))
      return Constants.kCreate;
    else if(op.equals(getSharedResString(Constants.kOperationDelete)))
      return Constants.kDelete;
    else
      return -1;
  }
  
  /**
   * @param type
   * @param key
   * @return
   */
  public static String getProperty(final String type, final String key) {
    try {
      return application.getAppProperties().getProperty(key);
    }
    catch(Exception e) {
      // Should only occur once in many calls
      return Preferences.getProps().getProperty(key);
    }
  }

  /**
   * @param type
   * @param key
   * @param def
   * @return
   */
  public static String getProperty(final String type, final String key, final String def) {
    try {
      return application.getAppProperties().getProperty(key, def);
    }
    catch(Exception e) {
      // Should only occur once in many calls
      return Preferences.getProps().getProperty(key, def);
    }
  }

  /**
   * @param patterns
   * @return
   */
  public static List<Property> getPropertiesByWildcard(final String... patterns) {
    final List<Property> vals = new ArrayList<>();    
    final com.jmoss.util.Properties props = application.getAppProperties();
    for(String prop:props.getPropertyNames()) {
      for(int i = 0; i < patterns.length; i++) {
        if(Utils.wildCardMatch(prop, patterns[i], Utils.isUnix())) {
          vals.add(new Property(prop, props.getProperty(prop)));
        }
      }
    }
    
    return vals;
  }

  /**
   * @param key
   * @param val
   * @return
   */
  public static Object putProperty(final Object key, final Object val) {
    try {
      return application.getAppProperties().put(key, val);
    }
    catch(NullPointerException e) {
      System.err.println(String.format("%s [?] [?] %s Application Properties not yet initialized: Cannot call putProperty(%s,%s)", Utils.formatLocalDateTime(Calendar.getInstance().getTime()), Thread.currentThread().getName(), key, val));
      return null;
    }
  }

  /**
   * @return
   */
  public static String getPropertiesLocation() {
    return application.getAppProperties().getLocation(application);
  }

  /**
   * @param lc
   * @throws IOException
   */
  public static void load(final IxLogClient lc) throws IOException {
    application.getAppProperties().load(application, lc);
  }

  /**
   * @param lc
   * @throws IOException
   */
  public static void store(final IxLogClient lc) throws IOException {
    application.getAppProperties().store(application, application.getProductName() + " " + Preferences.kName, lc);
  }

  /**
   * @param s
   * @return
   */
  public static String getSharedResString(final String s) {
    LogMgr.putTrace(instance.getClass(), "getSharedResString");
    String sr;

    if(s.endsWith(Constants.kActionResSuffix)
      || s.endsWith(Constants.kSoundResSuffix))
    {
      ErrorManager.showError(instance, "Can't call getSharedResString with an action or sound ref:" + s, false);
      sr = s;
    }
    else {
      try {
        sr = res.getString(s);
      }
      catch(MissingResourceException x) {
        AppMgr.logError(instance, Java.getMessage(x));
        sr = s;
      }
      catch(NullPointerException x) {
        ErrorManager.showError(instance, Java.getMessage(x), false);
        sr = s;
      }
    }
    
    LogMgr.popTrace("getSharedResString");
    return sr;
  }

  /**
   * @param args
   * @return
   */
  public static List<String> getSharedResStrings(final String ... args) {
    final List<String> labels = new ArrayList<>();
    for(int i = 0; i < args.length; i++) {
      labels.add(getSharedResString(args[i]));
    }
    
    return labels;
  }
  
  /**
   * @param s
   * @return
   */
  public static String[] getSharedResStringArray(final String s) {
    LogMgr.putTrace(instance.getClass(), "getSharedResStringArray");
    String[] a;
    
    if(s.endsWith(Constants.kActionResSuffix)
      || s.endsWith(Constants.kIconResSuffix)
      || s.endsWith(Constants.kSoundResSuffix))
    {
      ErrorManager.showError(instance, "Can't call getSharedResStringArray with an action, icon, or sound ref:" + s, false);
      a = Utils.toArray(s);
    }
    else {
      try {
        a = res.getStringArray(s);
      }
      catch(MissingResourceException x) {
        AppMgr.logError(instance, Java.getMessage(x));
        a = Utils.toArray(s);
      }
      catch(NullPointerException x) {
        ErrorManager.showError(instance, Java.getMessage(x), false);
        a = Utils.toArray(s);
      }
    }
    
    LogMgr.popTrace("getSharedResStringArray");
    return a;
  }
    
  /**
   * @param name
   * @return
   */
  public static String getImageUrl(final String name) {
    return ru.getImageUrl(name);    
  }

  /**
   * @param s
   * @return 
   */
  public static String getSoundURL(final String s) {
    LogMgr.putTrace(instance.getClass(), "getSoundURL");
    String url;
    
    try {
      url = ru.getSoundURL(s).toString();
    }
    catch(final Exception e) {
      url = "failure.au";
      ErrorManager.addError(instance, ErrorModel.createWarning(Java.getMessage(e), "Problem getting sound url for " + s, Utils.mkstr("Are the sound files under the runtime directory {0}?", ru.getResourcePackageName() + ru.getSoundsPackageName())));
    }
    
    LogMgr.popTrace("getSoundURL");
    return url;
  }

  /**
   * @param s
   * @return
   */
  public static String getSoundString(final String s) {
    LogMgr.putTrace(instance.getClass(), "getSoundString");
    String sound;
    
    if(s.endsWith(Constants.kSoundResSuffix)) {
      try {
        sound = res.getString(s);
      }
      catch(MissingResourceException x) {
        sound = "failure.au";
      }
    }
    else {
      ErrorManager.showError(instance, "Must call getSoundString with a sound ref:" + s, false);
      sound = "failure.au";
    }
    
    LogMgr.popTrace("getSoundString");
    return sound;
  }

  /**
   * @param msg
   */
  public static void logError(final String msg) {
    try {
      LogMgr.logError(instance, msg);
    }
    catch(final Exception e) {
      System.err.println(getSharedResString(Constants.kUninitialized) + msg);
    }
  }

  /**
   * @param msg
   */
  public static void logEvent(final String msg) {
    try {
      LogMgr.logEvent(instance, msg);
    }
    catch(final Exception e) {
      System.err.println(getSharedResString(Constants.kUninitialized) + msg);
    }
  }

  /**
   * @param msg
   * @param execute -- result of a boolean expression indicating whether to actually output the DEBUG info
   */
  public static void logDebug(final String msg, final boolean execute) {
    if(execute) {
      try {
        LogMgr.logDebug(instance, msg);
      }
      catch(final Exception e) {
        System.err.println(getSharedResString(Constants.kUninitialized) + msg);
      }
    }
  }

  /**
   * @param ex
   */
  public static void logTrace(final Throwable ex) {
    try {
      LogMgr.logTrace(instance, ex, false);
    }
    catch(final Exception e) {
      System.err.println(getSharedResString(Constants.kUninitialized) + Java.getMessage(ex));
    }
  }

  /**
    * Log an error
    * @param client
    * @param msg
    */
   public static void logError(final IxLogClient client, final String msg) {
     try {
      LogMgr.logError(client, msg);
     }
     catch(final Exception e) {
       System.err.println(getSharedResString(Constants.kUninitialized) + msg);
     }
   }

  /**
   * Log an error
   * @param client
   * @param name
   * @param msg
   */
   public static void logError(final IxLogClient client, final String name, final String msg) {
     try {
      LogMgr.logError(client, name, msg);
     }
     catch(final Exception e) {
       System.err.println(getSharedResString(Constants.kUninitialized) + msg);
     }
   }

  /**
   * Log a status event
   * @param client
   * @param msg
   * @return
   */
  public static String logEvent(final IxLogClient client, String msg) {
    try {
      return LogMgr.logEvent(client, msg);
    }
    catch(final Exception e) {
      msg = getSharedResString(Constants.kUninitialized) + msg;
      System.err.println(msg);
      return msg;
    }
  }

  /**
   * Log a status event
   * @param client
   * @param name
   * @param msg
   */
  public static void logEvent(final IxLogClient client, final String name, final String msg) {
    try {
      LogMgr.logEvent(client, name, msg);
    }
    catch(final Exception e) {
      System.err.println(getSharedResString(Constants.kUninitialized) + msg);
    }
  }

  /**
   * Log a debug event, conditionally
   * @param client
   * @param msg
   * @param execute  result of a boolean expression indicating whether to actually output the DEBUG info
   */
  public static void logDebug(final IxLogClient client, final String msg, final boolean execute) {
    if(execute) {
      try {
        LogMgr.logDebug(client, msg);
      }
      catch(final Exception e) {
        System.err.println(getSharedResString(Constants.kUninitialized) + msg);
      }
    }
  }

  /**
   * Log a debug event, conditionally
   * @param client
   * @param ex
   * @param execute  result of a boolean expression indicating whether to actually output the DEBUG info
   */
  public static void logDebug(final IxLogClient client, final Throwable ex, final boolean execute) {
    if(execute) {
      try {
        LogMgr.logDebug(client, ex);
      }
      catch(final Exception e) {
        System.err.println(getSharedResString(Constants.kUninitialized) + Java.getMessage(ex));
      }
    }
  }

  /**
   * Log a stack trace
   * @param client
   * @param ex
   * @param allPackages
   */
  public static void logTrace(final IxLogClient client, final Throwable ex, final boolean allPackages) {
    try {
      LogMgr.logTrace(client, ex, allPackages);
    }
    catch(final Exception e) {
      System.err.println(getSharedResString(Constants.kUninitialized) + Java.getMessage(ex));
    }
  }

  /**
   * Log a stack trace
   * @param client
   * @param ex
   * @param allPackages
   */
  public static void logTrace(final IxLogClient client, final String name, final Throwable ex, final boolean allPackages) {
    try {
      LogMgr.logTrace(client, ex, allPackages);
    }
    catch(final Exception e) {
      System.err.println(getSharedResString(Constants.kUninitialized) + Java.getMessage(ex));
    }
  }

  /**
   * Log an object dump
   * @param client
   */
  public static void logDump(final IxLogClient client) {
    try {
      LogMgr.logDump(client);
    }
    catch(final Exception e) {
      System.err.println(getSharedResString(Constants.kUninitialized) + Utils.notNull(client, P10nMgr.annotate("Null Client")));
    }
  }

  /**
   * Based on IxClientCallbacks
   * Allows CallBacks implementor to delegate to the application class.
   */
  public static void feedbackAudible(final String fileName) {
    try {
      application.feedbackAudible(fileName);
    }
    catch(final Exception e) {
      // Infrequent case where application does not yet exist
      UIFactory.beep();
    }
  }

  /**
   * Based on IxClientCallbacks
   * Allows CallBacks implementor to delegate to the application class.
   * @param base
   * @return
   */
  public static String createApplicationFolder(final String base) {
    try {
      return application.createApplicationFolder(base);
    }
    catch(final Exception e) {
      // Infrequent case where application does not yet exist
      return null;
    }
  }
  
  /**
   * Based on IxClientCallbacks
   * Allows CallBacks implementor to delegate to the application class.
   */
  public static String getApplicationFolder() {
    try {
      return application.getApplicationFolder();
    }
    catch(final Exception e) {
      // Infrequent case where application does not yet exist
      return null;
    }
  }

  /**
   * @return the directory folder path
   */
  public static String getDataFolder() {
    return getApplicationFolder() + File.separator + DAO.kData;
  }
  
  /**
   * Based on IxClientCallbacks
   * Allows CallBacks implementor to delegate to the application class.
   *
   * Obtain the full path to the Temp directory folder. (Release)
   * @return the directory folder path
   */
  public static String getTempPath() {
    try {
      return application.getTempPath();
    }
    catch(final Exception e) {
      // Infrequent case where application does not yet exist
      return null;
    }
  }

  /**
   * Based on IxClientCallbacks
   * Allows CallBacks implementor to delegate to the application class.
   */
  public static Integer getMajorRevision() {
    return application.getMajorRevision();
  }

  /**
   * Based on IxClientCallbacks
   * Allows CallBacks implementor to delegate to the application class.
   */
  public static Integer getMinorRevision() {
    return application.getMinorRevision();
  }

  /**
   * Based on IxClientCallbacks
   * Allows CallBacks implementor to delegate to the application class.
   */
  public static Integer getBugRevision() {
    return application.getBugRevision();
  }

  /**
   * Based on IxClientCallbacks
   * Allows CallBacks implementor to delegate to the application class.
   */
  public static Integer getMicroRevision() {
    return application.getMicroRevision();
  }

  /**
   * Based on IxClientCallbacks
   * Allows CallBacks implementor to delegate to the application class.
   */
  public static Integer getBuildType() {
    String target = System.getProperty("target");
    if(target == null) {
      return application.getBuildType();
    }
    else {
      return application.getTargetType(target);
    }        
  }

  /**
   * Based on IxClientCallbacks
   * Allows CallBacks implementor to delegate to the application class.
   */
  public static Integer getClientType() {
    return application.getClientType();
  }

  /**
   * Based on IxClientCallbacks
   * Allows CallBacks implementor to delegate to the application class.
   */
  public static String getBuildStamp() {
    return application.getBuildStamp();
  }
  
  /**
   * Based on IxClientCallbacks
   * Allows CallBacks implementor to delegate to the application class.
   * Callers may want to cache this response if it will be made repeatedly
   * @return
   */
  public static String getProductName() {
    return application.getProductName();
  }
  
  /**
   * Based on IxClientCallbacks
   * Allows CallBacks implementor to delegate to the application class.
   * @return
   */
  public static String getProductFamilyName() {
    return application.getProductFamilyName();
  }
  
  /**
   * Based on IxClientCallbacks
   * Allows CallBacks implementor to delegate to the application class.
   * @return
   */
  public static String getProductDescriptor() {
    return String.format("%s-%s", application.getProductFamilyName(), application.getProductName());
  }
  
  /**
   * Based on IxClientCallbacks
   * Allows CallBacks implementor to delegate to the application class.
   * @return
   */
  public static String getCopyright() {
    return application.getCopyright();
  }
  
  /**
   * Based on IxClientCallbacks
   * Allows CallBacks implementor to delegate to the application class.
   * @return
   */
  public static String getCompanyName() {
    return application.getCompanyName();
  }
  
  /**
   * @return
   */
  public static String getClientVersion() {
    StringBuilder buf = new StringBuilder();
    buf.append(application.getMajorRevision().toString());
    buf.append('.');
    buf.append(application.getMinorRevision().toString());
    buf.append('.');
    buf.append(application.getBugRevision().toString());
    buf.append('.');
    buf.append(application.getMicroRevision().toString());
    if(application.getBuildType().equals(Constants.kTypeDevelopment)) {
      buf.append('d');
    }
    
    return buf.toString();
  }

  /**
   * TODO: This will need to call a registration service that allocates globally-unique strings
   * 
   * For now, it depends on a jvm argument -Dinstance=MyInstance which works as long as these are unique
   * @return
   */
  public static String getServerInstance() {
    return System.getProperty("instance", "0");
  }
  
  /**
   * TODO: Make dynamic using class loader - generalize kb plugin mechanism for Common
   * @param name
   * @param user
   * @param start
   * @param end
   * @param status
   * @param params
   */
  public static void addMetric(String name, String user, Date start, Date end, String status, Map params) {
    LogMgr.putTrace(instance.getClass(), "addMetric");
    
    InstrumentationManager im = InstrumentationManager.get(user);
    im.add(name, user, start, end, status, params);
    
    LogMgr.popTrace("addMetric");
  }
  
  /**
   * Adds a PropertyChangeListener to the listener list.
   * The listener is registered for all properties.
   *
   * A PropertyChangeEvent will getDependents fired in response to setting
   * a property.
   *
   * @param listener  the PropertyChangeListener to be added
   */
  public static synchronized void addPropertyChangeListener(PropertyChangeListener listener) {
    //if(changeSupport == null) {
    //  changeSupport = new PropertyChangeSupport(this);
    //}
    
    //changeSupport.addPropertyChangeListener(listener);

    listenerList.add(listener);
  }

  /**
   * Adds a PropertyChangeListener for a specific property.  The listener
   * will be invoked only when a call on firePropertyChange names that
   * specific property.
   * 
   * If listener is null, no exception is thrown and no action is performed.
   * 
   * @param propertyName the seek of the property to listen on
   * @param listener the PropertyChangeListener to be added
   */
  public static synchronized void addPropertyChangeListener(final String propertyName, final PropertyChangeListener listener) {
    if(listener == null) {
      return;
    }
    
    if(changeSupport == null) {
      changeSupport = new PropertyChangeSupport(instance);
    }
    
    changeSupport.addPropertyChangeListener(propertyName, listener);
  }

  /**
   * Supports reporting bound property changes.  If oldValue and
   * newValue are not equal and the PropertyChangeEvent listener list
   * isn't empty, then fire a PropertyChange event to each listener.
   * This method has an overloaded method for each primitive referenceSourceType.  For
   * example, here's how to write a bound property set method whose
   * column is an int:
   * <pre>
   * public void setFoo(int newValue) {
   * int oldValue = foo;
   * foo = newValue;
   * firePropertyChange(foo, oldValue, newValue);
   * }
   * </pre>
   *
   * @param propertyName the programmatic seek of the property that was changed
   * @param oldValue the old column of the property
   * @param newValue the new column of the property
   * @see java.beans.PropertyChangeSupport
   */
  public static void firePropertyChange(final String propertyName, final Object oldValue, final Object newValue) {
    if(changeSupport != null) {
      changeSupport.firePropertyChange(propertyName, oldValue, newValue);
    }
  }

  /**
   * Removes a PropertyChangeListener from the listener list.
   * This removes a PropertyChangeListener that was registered
   * for all properties.
   *
   * @param listener  the PropertyChangeListener to be removed
   */
  public static synchronized void removePropertyChangeListener(PropertyChangeListener listener) {
    if(changeSupport != null) {
      changeSupport.removePropertyChangeListener(listener);
    }
    
    listenerList.remove(listener);
  }

  /**
   * Removes a PropertyChangeListener for a specific property.
   * If listener is null, no exception is thrown and no action is performed.
   * 
   * @param propertyName the seek of the property that was listened on
   * @param listener the PropertyChangeListener to be removed
   */
  public static synchronized void removePropertyChangeListener(final String propertyName, final PropertyChangeListener listener) {
    if(listener == null) {
      return;
    }
    
    if(changeSupport == null) {
      return;
    }
    
    changeSupport.removePropertyChangeListener(propertyName, listener);
  }

  /**
   * @return
   */
  public static synchronized PropertyChangeListener[] getPropertyChangeListeners() {
    return listenerList.toArray(new PropertyChangeListener[listenerList.size()]);
  }

  /**
   * Accessor
   * @param defaultDAO
   */
  public static void setDefaultDAO(final DAO defaultDAO) {
    AppMgr.defaultDAO = defaultDAO;
  }

  /**
   * Accessor
   * @return
   */
  public static DAO getDefaultDAO() {
    return defaultDAO;
  }

  /**
   * @param server
   * @return
   */
  public static DateDAO getDateDAO(final String server) {
    try {
      String source = getApplicationFolder() + File.separator + DAO.kData + File.separator + Constants.kConnectionDates;
      DAOFactory f = DAOFactory.getDAOFactory(DAO.kTypeMsAccess, server, source, -1, "", "", false);
      if(f != null) {
        return f.getDateDAO();
      }
    }
    catch(final ClassNotFoundException e) {
      ErrorManager.addError(instance, "Interface or abstract class: " + Java.getMessage(e));
    }
    catch(final IllegalAccessException e) {
      ErrorManager.addError(instance, "Access denied: " + Java.getMessage(e));
    }
    catch(final InstantiationException e) {
      ErrorManager.addError(instance, "Unresolved external: " + Java.getMessage(e));
    }
    catch(final DAOException e) {
      ErrorManager.addError(instance, "Data access error: " + Java.getMessage(e));
    }
    
    return null;
  }

  /**
   * @return
   */
  public static int generateKey() {
    return (int) (Math.random() * Integer.MAX_VALUE);
  }
  
  /**
   * Query the default data source for an auto increment.
   * If that doesn't exist, use the built-in random number generator.
   * @return 
   */
  public static int getKey() {
    try {
      return connectionManager.getConnectionFactory(defaultDAO.getName()).getKey();
    }
    catch(final Exception e) {
      Random r = Utils.getRandom();
      return r.nextInt();
    }
  }

  /**
   * @return
   */
  public static String getSessionId() {
    return kSessionId;
  }

  /**
   * IxLogClient
   * Every log entry written by this object starts with a certain prefix, after timestamp, id, and thread tags.
   * @return 
   */
  public String logEntryPrefix() {
    return "Common Application Manager";
  }

  /**
   * IxLogClient
   * Dump the contents of the object to a newline-delimited string.
   * @param verbose
   * @return
   */
  public String dump(final int verbose) {
    if(verbose >= 0) {
      final StringWriter sw = new StringWriter();
      final PrintWriter pw = new PrintWriter(sw);

      pw.println(LogMgr.getDumpHeader(getClass()));
      Java.printObject(pw, this);
      pw.println(Utils.lines(listenerList));
      pw.println(AppMgr.getLogMgr().getDump(addressBookManager, P10nMgr.annotate("No Address Book Manager")));
      pw.println(AppMgr.getLogMgr().getDump(commandManager, P10nMgr.annotate("No Command Manager")));
      pw.println(AppMgr.getLogMgr().getDump(connectionManager, P10nMgr.annotate("No Connection Manager")));
      pw.println(AppMgr.getLogMgr().getDump(bookmarksManager, P10nMgr.annotate("No Bookmarks Manager")));
      pw.println(AppMgr.getLogMgr().getDump(userManager, P10nMgr.annotate("No User Manager")));
      pw.println(AppMgr.getLogMgr().getDump(contentManager, P10nMgr.annotate("No Content Manager")));
      pw.println(ActionManager.getInstance().dump(verbose));
      pw.println(ErrorManager.getInstance().dump(verbose));
      pw.println(ValidationManager.getInstance().dump(verbose));
      pw.println(LogMgr.getDumpFooter(getClass()));

      return sw.toString();
    }
    else {
      return "";
    }
  }

  /**
   * IxErrorClient
   * Every log entry written by this object starts with a certain prefix, after timestamp, id, and thread tags.
   * @return 
   */
  public String errorEntryPrefix() {
    return "Common Application Manager";
  }
}
