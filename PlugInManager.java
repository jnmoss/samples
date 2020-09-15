/*
 * @(#)PlugInManager.java  10/10/2001
 *
 * Copyright 1995-2020 Jeffrey Moss All Rights Reserved.
 *
 */

package com.jmoss;

import com.jmoss.data.ConnectionFactory;
import com.jmoss.data.DAO;
import com.jmoss.data.IXmlRootMembers;
import com.jmoss.data.NameValuePair;
import com.jmoss.data.XML;
import com.jmoss.data.XmlDAO;
import com.jmoss.event.ssEventListenerList;
import com.jmoss.plugins.AxPlugIn;
import com.jmoss.plugins.IxPlugIn;
import com.jmoss.plugins.IxPlugInListener;
import com.jmoss.ui.P10nMgr;
import com.jmoss.ui.PAO;
import com.jmoss.util.ErrorManager;
import com.jmoss.util.ErrorModel;
import com.jmoss.util.IxErrorClient;
import com.jmoss.util.JarClassLoader;
import com.jmoss.util.JarResources;
import com.jmoss.util.Java;
import com.jmoss.util.Lists;
import com.jmoss.util.LogMgr;
import com.jmoss.util.ResourceUtilities;
import com.jmoss.util.Text;
import com.jmoss.util.Utils;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * @version $Revision$
 * Last Updated By: $Author$
 * Last Updated On: $Date$
 */
public class PlugInManager extends AxMutableManager implements IxPlugInListener, IXmlRootMembers, IxErrorClient {
  
  public static final String kPDFConverter = "PDF Converter";
  public static final String kGraphingPackage = "Graphing Package";
  
  public static final String ADD = "add";
  public static final String GET = "get";
  public static final String DELETE = "delete";
  public static final String UPDATE = "update";
  public static final String SUPPORT = "support";
  public static final String LOOKUP = "Lookup";

  protected static final String kPluginsXmlTagName = "plugins";
  protected static final String kPluginXmlTagName = "plugin";
  protected static final String kVersionXmlValue = "1.0";
  protected static final String kRefreshed = "refreshed";
  
  protected static String kName = "Common Plug-In Manager";

  /** Map of JAR names to their loaders */
  protected static final Map<String,JarClassLoader> loaders = new HashMap<>();

  /** Comments to place at the top of the configuration file */
  protected final List<String> comments = new ArrayList<>();

  /** Map of plugin names to plugins */
  protected Map<String,AxPlugIn> plugins = new Hashtable<>();
  
  /** Plugins map be unmapped after initial load because host attribute was not yet available */
  protected List<AxPlugIn> unmapped = new ArrayList<>();

  protected SAXParser theParser;

  private PlugInHandler pluginHandler;
  
  /** EventListener List */
  private final ssEventListenerList listenerList = new ssEventListenerList();

  /** Files used to serialize to/from */
  protected File serializationFile;
  private File tempSerializationFile;
  
  private String host;

  /** Hashtable used to store version attribute */
  private static final Map<String,String> xmlAtts = new Hashtable<>(9);
  
  private static PlugInManager instance;

  /**
   * @param dataFile
   * @param l
   * @throws IOException
   */
  public PlugInManager(final String dataFile, final IxPlugInListener l) throws IOException {
    
    // Ensure certain Plugin properties are not empty
    if(AppMgr.getProperty(Constants.kConfig, "plugin.directory") == null) {
      AppMgr.putProperty("plugin.directory", ResourceUtilities.getCodeBase("com.jmoss.plugins"));
      
      try {
        AppMgr.store(this);
      }
      catch(final IOException ioe) {
        AppMgr.logTrace(this, ioe, false);
        ErrorManager.queue(this, this, Java.getMessage(ioe));
      }
    }

    // Set serialization files
    serializationFile = new File(dataFile);
    tempSerializationFile = new File(dataFile + '~');

    comments.add(dataFile);
    comments.add("The Plug-In JAR directory will be found using the property \"plugin.directory\" with the default being ResourceUtilities.getCodeBase(\"com.jmoss.plugins\")");
    comments.add("The Plug-In JAR name will be determined using <plugin path=\"a/b/c/name.class ...\", with precedence 1) the name with a \"-\" inserted before the \"PlugIn\" ending, e.g. \"MyPlugIn\" -> \"My-PlugIn\" 2) the part of the name to the left of the String \"PlugIn\"");

    // Set-up version information
    xmlAtts.put(XmlDAO.kVersionAtt, kVersionXmlValue);
    xmlAtts.put("timestamp", Utils.key());
    xmlAtts.put("product", AppMgr.getProductDescriptor());
    xmlAtts.put("copyright", AppMgr.getCopyright());
    xmlAtts.put("company", AppMgr.getCompanyName());
    xmlAtts.put("revision", String.format("%s.%s.%s", AppMgr.getMajorRevision(), AppMgr.getMinorRevision(), AppMgr.getMicroRevision()));
    xmlAtts.put("build", AppMgr.getBuildStamp());
    
    // Add the initial listener before loading any saved plug-ins
    addPlugInListener(l);
    
    // Need instance of the Manager initialized before loading any plugins
    instance = this;

    // Load plugIns
    if(serializationFile.exists()) {
      loadXml();

      if(false) {
        Set<String> keys = plugins.keySet();
        for(String key : new ArrayList<>(keys)) {
          AxPlugIn plugin = plugins.get(key);
          if(plugin.getHost().equals(AxPlugIn.kNoHost) == false) {
            plugins.remove(plugin.getName());
            plugins.put(plugin.getHost().toString(), plugin);
          }
        }
      }
      
      for(AxPlugIn plugin : unmapped) {
        if(plugin.getHost().equals(AxPlugIn.kNoHost) == false) {
          plugins.put(plugin.getHost().toString(), plugin);
        }
      }
    }
    
    populateSupportedTypes();
    
    LogMgr.listProperties();
  }

  /**
   * @return
   */
  public int getPlugInCount() {
    return plugins.size();
  }

  /**
   * Find plugIn based on key.
   * @param name
   * @return
   */
  public AxPlugIn get(final String name) {
    return plugins.get(name);
  }

  /**
   * Find plugIn based on object equality.
   * @param _plugIn
   * @return
   */
  public AxPlugIn get(final AxPlugIn _plugIn) {
    return plugins.get(_plugIn.getName());
  }

  /**
   * @param _plugIn
   */
  public void setRefreshed(final AxPlugIn _plugIn) {
    final List<NameValuePair> nvs = _plugIn.getNameValuePairs();
    if(nvs != null) {
      final List<NameValuePair> refreshed = NameValuePair.findByName(nvs, kRefreshed);
      if(refreshed.size() == 1) {
        refreshed.get(0).setValue(String.valueOf(System.currentTimeMillis()));
      }
      else {
        _plugIn.addNameValuePair(new NameValuePair(NameValuePair.READONLY_ID, "", kRefreshed, String.valueOf(System.currentTimeMillis())));
      }
    }
    else {
      _plugIn.addNameValuePair(new NameValuePair(NameValuePair.READONLY_ID, "", kRefreshed, String.valueOf(System.currentTimeMillis())));
    }
  }

  /**
   * @param _plugIn
   * @return
   */
  public Date getRefreshed(final AxPlugIn _plugIn) {
    final List<NameValuePair> nvs = _plugIn.getNameValuePairs();
    if(nvs != null) {
      final List<NameValuePair> refreshed = NameValuePair.findByName(nvs, kRefreshed);
      if(refreshed.size() >= 1) {
        return new Date(Long.valueOf(refreshed.get(0).getValue()));
      }
      else {
        return null;
      }
    }
    else {
      return null;
    }
  }

  /**
   * @param pluginName
   * @param className
   * @return
   */
  public Class fetchClass(final String pluginName, String className) {
    JarClassLoader loader = loaders.get(pluginName);
    if(loader != null) {
      boolean b = Java.isClassFile(className);
      if(b == false) {
        className = Java.fromBinaryName(className, '/');
        className = Java.toClassFile(className);
      }

      return loader.fetchClass(className);
    }

    return null;
  }

  /**
   * @param cmd
   * @return
   */
  public List<AxPlugIn> findByActionCommand(final String cmd) {
    List<AxPlugIn> found = new ArrayList<>();
    for(AxPlugIn aPlugIn : plugins.values()) {
      if(aPlugIn.getMenuItem().getText().equals(cmd)) {
        found.add(aPlugIn);
      }
    }
    
    return found;
  }

  /**
   * @param host
   * @return
   */
  public List<AxPlugIn> findByHost(final Object host) {
    List<AxPlugIn> found = new ArrayList<>();
    for(AxPlugIn aPlugIn : plugins.values()) {
      if(aPlugIn.getHost().equals(host)) {
        found.add(aPlugIn);
      }
    }
    
    return found;
  }

  /**
   * @param host
   * @param nv
   * @return
   */
  public List<AxPlugIn> findByHost(final Object host, final NameValuePair nv) {
    List<AxPlugIn> found = new ArrayList<>();
    for(AxPlugIn aPlugIn : plugins.values()) {
      if(aPlugIn.getHost().equals(host)) {
        final List<NameValuePair> nvs = aPlugIn.getNameValuePairs();
        if(Utils.contains(nvs, nv)) {
          found.add(aPlugIn);
        }
      }
    }
    
    return found;
  }

  /**
   * @param key
   * @return
   */
  public AxPlugIn findByKey(final Long key) {
    Date d = new Date(key);
    for(AxPlugIn aPlugIn : plugins.values()) {
      if(aPlugIn.getInstalled().equals(d)) {
        return aPlugIn;
      }
    }
    
    return null;
  }

  /**
   * Accessor
   * @return 
   */
  public Collection<AxPlugIn> getPlugIns() {
    return plugins.values();
  }

  /**
   * Check to see if plug-in already exists and return false if so
   * @param _plugIn
   * @return
   * @throws IOException
   */
  public boolean addPlugIn(final AxPlugIn _plugIn) throws IOException {
    if(plugins.get(_plugIn.getName()) == null) {
      if(_plugIn.getHost().equals(AxPlugIn.kNoHost) == false) {
        plugins.remove(_plugIn.getName());
        plugins.put(_plugIn.getHost().toString(), _plugIn);
      }
      else {
        plugins.put(_plugIn.getName(), _plugIn);
      }
      
      // Write these changes out
      saveXml();

      firePlugInAdded(_plugIn);
      return true;
    }
    else {
      return false;
    }
  }

  /**
   * Check to see if plug-in exists and return false if not
   * @param plugIn
   * @return
   * @throws IOException
   */
  public boolean removePlugIn(final AxPlugIn plugIn) throws IOException {
    if(plugins.remove(plugIn.toString()) != null) {

      // Write these changes out
      saveXml();

      firePlugInRemoved(plugIn);
      return true;
    }
    else {
      return false;
    }
  }
    
  /**
   * @param plugin
   * @return
   */
  public static ActionListener actionListener(final AxPlugIn plugin) {
    ActionListener actionListener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        try {
          plugin.setAction(e.getSource().toString());
          AppMgr.getCommandManager().execute(plugin.getCommand());
          plugin.getCommand().completed();
          AppMgr.getCommandManager().getMainListener().commandCompleted(plugin.getCommand());
        }
        catch(Error err) {
          plugin.getCommand().aborted(Java.getMessage(err));
          AppMgr.getCommandManager().getMainListener().commandAborted(plugin.getCommand(), Java.getMessage(err));
          ErrorManager.showException(instance, ErrorModel.createSevereError("Execution Error", Java.getMessage(err), ErrorModel.kActionReturn), kName, err);
        }
        catch(final Exception ex) {
          plugin.getCommand().aborted(Java.getMessage(ex));
          AppMgr.getCommandManager().getMainListener().commandAborted(plugin.getCommand(), Java.getMessage(ex));
          ErrorManager.showException(instance, ErrorModel.createSevereError("Execution Error", Java.getMessage(ex), ErrorModel.kActionReturn), kName, ex);
        }
      }
    };

    return actionListener;
  }

  /**
   * @param listener
   */
  public void addPlugInListener(final IxPlugInListener listener) {
    listenerList.add(IxPlugInListener.class, listener);
  }

  /**
   * Is the listener already listening?
   */
  public boolean isListening(IxPlugInListener listener) {
    Object[] listeners = listenerList.getListenerList();
    for(int i = listeners.length - 2; i >= 0; i -= 2) {
      if(listeners[i+1].equals(listener)) {
        return true;
      }
    }

    return false;
  }

  /**
   * @param listener
   */
  public void removePlugInListener(IxPlugInListener listener) {
    listenerList.remove(IxPlugInListener.class, listener);
  }

  /** */
  private void firePlugInAdded(AxPlugIn plugIn) {
    Object[] listeners = listenerList.getListenerList();
    for(int i = listeners.length - 2; i >= 0; i -= 2) {
      if(listeners[i] == IxPlugInListener.class && listeners[i] != this) {
        ((IxPlugInListener)listeners[i+1]).pluginAdded(plugIn);
      }
    }
  }

  /** */
  private void firePlugInRemoved(AxPlugIn plugIn) {
    Object[] listeners = listenerList.getListenerList();
    for(int i = listeners.length - 2; i >= 0; i -= 2) {
      if(listeners[i] == IxPlugInListener.class && listeners[i] != this) {
        ((IxPlugInListener)listeners[i+1]).pluginRemoved(plugIn);
      }
    }
  }

  /**
   * IxPlugInListener
   * @param plugin
   */
  public void pluginAdded(final IxPlugIn plugin) {
    try {
      addPlugIn((AxPlugIn) plugin);
    }
    catch(final IOException x) {
      ErrorManager.addError(this, ErrorModel.createNoteworthyError(Java.getMessage(x), "Problem adding plug-in "+plugin.getName(), ErrorModel.kActionNotify));
    }
  }
  
  /**
   * IxPlugInListener
   * @param plugin
   */
  public void pluginRemoved(final IxPlugIn plugin) {
    try {
      removePlugIn((AxPlugIn) plugin);
    }
    catch(final IOException x) {
      ErrorManager.addError(this, ErrorModel.createNoteworthyError(Java.getMessage(x), "Problem removing plug-in " + plugin.getName(), ErrorModel.kActionNotify));
    }
  }
  
  /**
   * Singleton
   * Factory Method
   * @param l
   * @return
   */
  public static PlugInManager getPlugInManager(final IxPlugInListener l) {
    if(instance == null) {
      try {
        // instance will be initialized in the constructor, but the assignment below is cosmetic and idempotent
        String datasource = AppMgr.createDataSourceNameData("PlugIns");
        instance = new PlugInManager(AppMgr.getApplicationFolder() + File.separator + datasource, l);
      }
      catch(final IOException e) {
        if(instance != null) {
          ErrorManager.queue(kName, instance, Java.getStackTrace(e, false));
        }
        else {
          ErrorManager.queue(kName, AppMgr.getInstance(), Java.getStackTrace(e, false));
        }
      }
    }
    
    return instance;
  }

  /**
   * @param op
   * @return
   */
  public boolean isSupported(final String op) {
    for(AxPlugIn aPlugIn : plugins.values()) {
      {
        List<NameValuePair> nvps = NameValuePair.findByName(aPlugIn.getNameValuePairs(), GET);
        for(NameValuePair nvp : nvps) {
          if(op.equals(nvp.getValue())) {
            return true;
          }
        }
      }
      
      {
        String att = Java.mkJavaName(op);
        List<NameValuePair> nvps = NameValuePair.findByName(aPlugIn.getNameValuePairs(), SUPPORT);
        for(NameValuePair nvp : nvps) {
          if(att.equals(nvp.getValue())) {
            String method = nvp.getName() + Java.mkJavaName(nvp.getValue());
            try {
              Method theMethod = aPlugIn.getClass().getDeclaredMethod(method);
              Boolean ret = (Boolean) theMethod.invoke(aPlugIn);
              if(ret) {
                return true;
              }
            }
            catch(final Exception x) {
              ErrorManager.addError(this, ErrorModel.createNoteworthyError(String.format("Problem invoking method %s on plug-in %s", P10nMgr.highlight(method), P10nMgr.highlight(aPlugIn.getName())), Java.getStackTrace(x, "", 1, false), ErrorModel.kActionContinue));
            }
          }
        }
      }
    }
    
    return false;
  }

  /**
   * The Plug-In JAR directory will be found using the property "plugin.directory" with the default being ResourceUtilities.getCodeBase("com.jmoss.plugins")
   * The Plug-In JAR name will be determined using <plugin path="a/b/c/name.class ...", with precedence 1) the name with a "-" inserted before the "PlugIn" ending, e.g. "MyPlugIn" -> "My-PlugIn" 2) the part of the name to the left of the String "PlugIn"
   */
  private static JarClassLoader jarLoader(final String fileName) throws Exception {
    String pd = AppMgr.getProperty(Constants.kConfig, "plugin.directory", ResourceUtilities.getCodeBase("com.jmoss.plugins"));
    String pluginName = Text.leftOfFromEnd(fileName, "PlugIn.class", 1, "");
    int idx = fileName.indexOf("PlugIn");

    String jarName;
    if(idx > 0) {
      jarName = pd + File.separator + fileName.substring(0, idx) + "-" + "PlugIn" + Java.SUFFIX_JAR;
      if(new File(jarName).exists() == false) {
        jarName = pd + File.separator + pluginName + Java.SUFFIX_JAR;
      }
    }
    else {
      jarName = pd + File.separator + pluginName + Java.SUFFIX_JAR;
    }

    JarClassLoader loader = new JarClassLoader(jarName);
    loaders.put(pluginName, loader);
    Java.addPath(jarName);
    return loader;
  }

  /**
   * @param path
   * @param pkg
   * @param ignoreErrors
   * @param thePAO
   * @return
   * @throws Exception
   */
  public static AxPlugIn createPlugIn(final String path, final String pkg, final String ignoreErrors, final String thePAO) throws Exception {
    LogMgr.putTrace(instance.getClass(), "createPlugIn");
    
    final File pluginFile = new File(path);
    final String pkgFile = Utils.isNullOrEmpty(pkg) 
      ? pluginFile.getName()
      : pkg + '.' + pluginFile.getName();
    
    AxPlugIn thePlugin = null;

    try {
      // If path is relative, e.g., com/jmoss/plugins/DictServicePlugIn.class, extract such that DictService and look for that as a JAR
      boolean isRelative = pluginFile.isAbsolute() == false;
      if(isRelative) {
        Set<String> ignored = new HashSet<>();
        String fileName = new File(path).getName();
        String pluginName = Text.leftOfFromEnd(fileName, "PlugIn.class", 1, "");

        JarClassLoader loader = loaders.get(pluginName);
        if(loader == null) {
          loader = jarLoader(fileName);
        }
        else {
          // Re-use loaded classes?
          boolean debug = true;
        }
        
        JarResources resources = loader.getJarResources();
        String jarName = resources.getJarFileName();
        JarFile jar = Java.loadJar(jarName);
        for(Enumeration<JarEntry> en = jar.entries(); en.hasMoreElements(); ) {
          JarEntry entry = en.nextElement();
          if(entry.toString().equalsIgnoreCase(JarFile.MANIFEST_NAME) == false) {
            if(Java.isClassFile(entry.toString())) {
              try {
                // LogMgr.logDebug(String.format("Loading class: %s", entry));
                Class c = loader.loadClass(entry.toString(), true, instance); // class au.com.bytecode.opencsv.CSVReader -- class com.splunk.ResultsReaderCsv
                if(c.isInterface() == false) {
                  if(entry.toString().equals(path)) {
                    Object theClass = null;
                    Constructor<?> theCtor = null;
                    if(thePAO.length() > 0) {
                      Constructor<?>[] constructors = c.getConstructors();
                      for(int i = 0; i < constructors.length; i++) {
                        Constructor<?> ctor = constructors[i];
                        Class<?>[] ptypes = ctor.getParameterTypes();
                        if(ptypes.length == 1 && ptypes[0] == int.class) {
                          theCtor = ctor;
                        }
                      }
                    }

                    if(theCtor != null) {
                      int paoType = PAO.getType(thePAO);
                      theClass = theCtor.newInstance(paoType);
                      AppMgr.logEvent(instance, AppMgr.getLogMgr().mkstr("Loaded class ({0}) using  ctor for ({1})", theClass.getClass().getName(), thePAO));
                    }
                    else {
                      theClass = c.newInstance();
                      AppMgr.logEvent(instance, AppMgr.getLogMgr().mkstr("Loaded class ({0}) using no-arg ctor", theClass.getClass().getName()));
                    }
                    
                    thePlugin = (AxPlugIn)theClass;
                    thePlugin.setPath(path);
                    thePlugin.setIgnoreErrors(Text.split(ignoreErrors, File.pathSeparator, ""));
                    thePlugin.initialize();
                  }
                }
              }
              catch(final Throwable e) {
                if(Text.indexOf(e.getMessage(), "duplicate") == -1) {
                  // Only way to ignore duplicate warning messages - hopefully the Throwable object will someday distinguish
                  if(ignoreErrors.contains(e.getClass().getName())) {
                    // e.g. "java.lang.IncompatibleClassChangeError"
                    ignored.add(e.getClass().getName());
                  }
                  else {
                    String msg = String.format("%s: %s %s %s (%s)", AppMgr.getSharedResString(Constants.kErrorLoad), jarName, Constants.kSymbolRightArrow, entry, Java.getStackTrace(e, Constants.NL_SUBSTITUTE, 2, false));
                    try {
                      ErrorManager.addWarning(instance, msg);
                    }
                    catch(Throwable err) {
                      AppMgr.logError(instance, msg);
                    }
                  }
                }
              }
            }
            else {
              if(entry.toString().endsWith("/") == false) {
                byte[] bytes = resources.getResource(entry.toString());
                String cd = new File("").getAbsolutePath();
                String base = Text.leftOfFromEnd(cd, pkg.replace('.', File.separatorChar), 1, "");
                fileName = base + entry.toString().replace('/', File.separatorChar);
                File f = new File(fileName);
                if(f.getParent() != null) {
                  Utils.verifyPath(f.getParent());
                }

                try {
                  FileOutputStream fo = new FileOutputStream(fileName);
                  fo.write(bytes);
                  fo.close();
                  AppMgr.logEvent(instance, AppMgr.getLogMgr().mkstr("Copied non-class file ({0})", fileName));
                }
                catch(Exception e) {
                  AppMgr.logEvent(instance, AppMgr.getLogMgr().mkstr("Did not process non-class file ({0})", entry));
                }
              }
              else {
                AppMgr.logEvent(instance, AppMgr.getLogMgr().mkstr("Did not process non-class file ({0})", entry));
              }
            }
          }
        }

        for(String s : ignored) {
          String msg = String.format("%s: %s %s (Configuration is ignoring errors of class %s)", AppMgr.getSharedResString(Constants.kWarning), jarName, Constants.kSymbolRightArrow, s);
          try {
            ErrorManager.addWarning(instance, msg);
          }
          catch(Throwable err) {
            AppMgr.logError(instance, msg);
          }
        }
        
        resources.clear();
      }
      else {
        Class theClass = ClassLoader.getSystemClassLoader().loadClass(Text.leftOfFromEnd(pkgFile, Java.SUFFIX_CLASS, 1, ""));
        Object theInstance = theClass.newInstance();
        AppMgr.logEvent(instance, AppMgr.getLogMgr().mkstr("Loaded class ({0})", theInstance.getClass().getName()));

        thePlugin = (AxPlugIn)theInstance;
        thePlugin.setPath(path);
      }

      LogMgr.popTrace("createPlugIn");
      return thePlugin;
    }
    catch(final Error e) {
      // Check the other classpath items, except for Jars
      final List<String> classPaths = Java.getClassPaths(false);
      final List<String> filtered = Lists.filter(classPaths, Java.SUFFIX_JAR, Constants.kNotEndsWith);
      Exception savedException = null;
      String pluginPath = pluginFile.getAbsolutePath();
      for(String aPath : filtered) {
        String relativePath = pluginPath.substring(aPath.length() + File.separator.length());
        String dottedPath = relativePath.replace(File.separator, ".");

        try {
          Object theClass = ClassLoader.getSystemClassLoader().loadClass(Text.leftOfFromEnd(dottedPath, Java.SUFFIX_CLASS, 1, "")).newInstance();
          AppMgr.logEvent(instance, AppMgr.getLogMgr().mkstr("Loaded class ({0})", theClass.getClass().getName()));

          AxPlugIn plugin = (AxPlugIn)theClass;
          plugin.setPath(path);
          return plugin;
        }
        catch(final Exception ex) {
          savedException = new Exception("Problem loading plug-in from: " + relativePath, ex);
        }
      }

      LogMgr.popTrace("createPlugIn");
      throw savedException;
    }
  }

  /**
   * IxManager
   * @return
   */
  public String getName() {
    return kName;
  }

  /**
   * IxPersistentManager
   * @return
   */
  public int getDaoType() {
    return DAO.kTypeXML;
  }

  /**
   * IxPersistentManager
   * @return
   */
  public String getHost() {
    return getByHeader(kHostHeader, Integer.MAX_VALUE).toString();
  }

  /**
   * IxPersistentManager
   * @return
   */
  public boolean isHistory() {
    return Boolean.valueOf(getByHeader(kHistoryHeader, Integer.MAX_VALUE).toString());
  }

  /**
   * IxPersistentManager
   */
  public void populateSupportedTypes() {
    daoTypes.add(DAO.kXML);
  }

  /**
   * IxMutableManager
   * @param oldType
   * @param newType
   * @return
   */
  public void changeDAO(final int oldType, final int newType) {
    if(oldType != newType) {
      throw new UnsupportedOperationException("The PlugInManager.changeDAO() method must be implemented");
    }
  }

  /**
   * IxMutableManager
   * Creates a duplicate of the source Manager with the same DAO.
   * Useful to hold changes to a Manager without modifying the source and before committing any changes.
   * @return
   */
  public IxMutableManager copy() {
    try {
      PlugInManager theCopy = (PlugInManager)clone();
      theCopy.plugins = new Hashtable<>(plugins);
      return theCopy;
    }
    catch(CloneNotSupportedException e) {
      ErrorManager.addError(this, Java.getMessage(e));
    }
    
    return null;
  }

  /**
   * IxMutableManager
   * Creates a copy of the source Manager with a new, temporary DAO without the expense of instantiating a new DAO object.
   * Useful to hold changes to a Manager without modifying the source and before committing any changes.
   * @param daoName
   * @return
   */
  public IxMutableManager mutate(String daoName) {
    if(daoName != DAO.kXML) {
      throw new UnsupportedOperationException("The PlugInManager.mutate() method must be implemented");
    }
    else {
      return this;
    }
  }

  /**
   * IxTableRow
   * Based on the filters headers return a representation of the object suitable for display in a table.
   * @return
   */
  public Collection toRow(final Collection currentHeaders, int mask) {
    if(theRow == null) {
      theRow = new Vector(currentHeaders.size());
    }
    else if(theRow.size() != currentHeaders.size()) {
      theRow = new Vector(currentHeaders.size());
    }
    else {
      theRow.clear();
    }
    
    for(Object next:currentHeaders) {
      Object val = getByHeader(next.toString(), Integer.MAX_VALUE);
      if(val != null) {
        theRow.add(val);
      }
    }

    return theRow;
  }

  /**
   * IxTableRow
   * @param header
   * @param val
   */
  public void setByHeader(final String header, final Object val) {
    if(header == kComponentHeader) {
      throw new UnsupportedOperationException("Cannot set Component header in setByHeader");
    }
    else if(header == kDaoHeader) {
      throw new UnsupportedOperationException("Cannot set DAO header in setByHeader");
    }
    else if(header == kHostHeader) {
      this.host = val.toString();
    }
    else if(header == kHistoryHeader) {
      throw new UnsupportedOperationException("Cannot set History header in setByHeader");
    }
  }

  /**
   * IxTableRow
   * @param header
   * @return 
   */
  public Object getByHeader(final String header, int mask) {
    if(header == kComponentHeader) {
      return this;
    }
    else if(header == kDaoHeader) {
      return DAO.kXML;
    }
    else if(header == kHostHeader) {
      return ConnectionFactory.kDefaultServer;
    }
    else if(header == kHistoryHeader) {
      return String.valueOf(false);
    }
    else {
      return null;
    }
  }

  /**
   * @return
   */
  @Override
  public String toString() {
    return getName();
  }

  /**
   * @param object
   * @return
   */
  public int compareTo(final Object object) {
    if(this == object) {
      return 0;
    }
    
    if(!(object instanceof IxManager)) {
      return 1;
    }
    
    final IxManager other = (IxManager)object;
    return getName().compareTo(other.getName());
  }

  /**
   * Class for parsing plugin lists as XML
   */
  private final class PlugInHandler extends DefaultHandler {

    SAXParser parser = null;

    PlugInHandler(SAXParser parser) {
      this.parser = parser;
    }

    /**
     * @param namespaceURI
     * @param lName
     * @param qName
     * @param attributes
     */
    public void startElement(String namespaceURI,
                             String lName, // local name
                             String qName, // qualified name
                             Attributes attributes) {
      LogMgr.putTrace(instance.getClass(), "startElement");

      String name = lName == "" ? qName : lName;
      if(name.equals(kPluginXmlTagName)) {
        try {
          String path = attributes.getValue("path");
          String disabled = attributes.getValue("disabled");
          if("1".equals(disabled)) {
            AppMgr.logEvent(instance, AppMgr.getLogMgr().mkstr("Skipping disabled plugin at ({0})", path));
          }
          else {
            AxPlugIn plugin = createPlugIn(path, attributes.getValue("package"), Utils.notNull(attributes.getValue("ignoreErrors")), Utils.notNull(attributes.getValue("PAO")));
            if(plugin != null) {
              plugin.readExternal(parser, this);
              if(plugin.isDisabled() == false) {
                AxPlugIn existing = plugins.get(plugin.getName());
                if(existing == null) {
                  plugins.put(plugin.getName(), plugin);
                }
                else {
                  unmapped.add(plugin);
                  // ErrorManager.queue(kName, instance, ErrorModel.createNoteworthyError(AppMgr.getSharedResString(Constants.kErrorParse), "Duplicate plug-in class for " + plugin.getName(), ErrorModel.kActionNotify));
                }
              }
            }
            else {
              String msg = String.format("Received null for (%s) - it may be incomplete and require a rebuild", path);
              try {
                ErrorManager.queue(kName, instance, ErrorModel.createNoteworthyError(AppMgr.getSharedResString(Constants.kErrorLoad), msg, ErrorModel.kActionNotify));
              }
              catch(Throwable err) {
                AppMgr.logError(instance, msg);
              }
            }
          }
        }
        catch(final InstantiationException e) {
          try {
            ErrorManager.queue(kName, instance, ErrorModel.createNoteworthyError("Unresolved external", Java.getMessage(e), ErrorModel.kActionNotify));
          }
          catch(Throwable err) {
            AppMgr.logError(instance, Java.getStackTrace(e, false));
          }
        }
        catch(final IllegalAccessException e) {
          try {
            ErrorManager.queue(kName, instance, ErrorModel.createNoteworthyError("Access denied", Java.getMessage(e), ErrorModel.kActionNotify));
          }
          catch(Throwable err) {
            AppMgr.logError(instance, Java.getStackTrace(e, false));
          }
        }
        catch(final ClassNotFoundException e) {
          try {
            ErrorManager.queue(kName, instance, ErrorModel.createNoteworthyError("Unresolved class or interface", Java.getMessage(e), ErrorModel.kActionNotify));
          }
          catch(Throwable err) {
            AppMgr.logError(instance, Java.getStackTrace(e, false));
          }
        }
        catch(final FileNotFoundException e) {
          try {
            ErrorManager.queue(kName, instance, ErrorModel.createNoteworthyError("Plug-in Jar file or other resource not found", Java.getMessage(e), ErrorModel.kActionNotify));
          }
          catch(Throwable err) {
            AppMgr.logError(instance, Java.getStackTrace(e, false));
          }
        }
        catch(final Exception e) {
          try {
            ErrorManager.queue(kName, instance, ErrorModel.createNoteworthyError(AppMgr.getSharedResString(Constants.kErrorParse), Java.getStackTrace(e, false), ErrorModel.kActionNotify));
          }
          catch(Throwable err) {
            AppMgr.logError(instance, Java.getStackTrace(e, false));
          }
        }
      }

      LogMgr.popTrace("startElement");
    }

    public void endElement(String uri, String lName, String qName) {
      if( lName.equals(kPluginsXmlTagName) ) { }
    }

    public void characters( char[] ch, int start, int length ) throws SAXException {
      // Nothing left for the Example 6 mapper to handle in the endElement SAX event.
    }

    public void warning (SAXParseException e) throws SAXException {
      // no op
    }

    public void error (SAXParseException e) throws SAXException {
      // no op
    }

    public void fatalError (SAXParseException e) throws SAXException {
      throw e;
    }
  }

  /**
   **/
  protected SAXParser getTheParser() throws ParserConfigurationException, SAXException {
    if(theParser == null) {
      SAXParserFactory spf = SAXParserFactory.newInstance();
      spf.setValidating(false); // No validation required
      theParser = spf.newSAXParser();
    }

    return theParser;
  }

  /**
   * IXmlRootMembers
   * Read in the list of user plugins as XML
   * @throws IOException
   */
  public void loadXml() throws IOException {
    InputStreamReader input = null;
    
    try {
      // Open up preferences file for reading
      input = new InputStreamReader(new BufferedInputStream(new FileInputStream(serializationFile)), "UTF8");

      SAXParser sp = getTheParser();
      InputSource source = new InputSource(input);

      pluginHandler = new PlugInHandler(sp);
      sp.parse(source, pluginHandler);
      for(AxPlugIn aPlugIn : plugins.values()) {
        firePlugInAdded(aPlugIn);            
      }
    }
    catch(final ParserConfigurationException | SAXException e) {
      try {
        ErrorManager.queue(kName, instance, Java.getStackTrace(e, Constants.NL_SUBSTITUTE, 2, false));
      }
      catch(Throwable err) {
        AppMgr.logError(instance, Java.getStackTrace(err, Constants.NL_SUBSTITUTE, 2, false));
      }
    }
    catch(final IOException e) {
      // In case the last invocation crashed and the config file was not finished, so use the backup version
      input = new InputStreamReader(new BufferedInputStream(new FileInputStream(tempSerializationFile)), "UTF8");
      InputSource source = new InputSource(input);
      try {
        SAXParser sp = getTheParser();
        sp.parse(source, pluginHandler);

        serializationFile.delete();
        tempSerializationFile.renameTo(serializationFile);

        AppMgr.logEvent(this, "Configuration file was restored from backup, possible Crash on prior invocation");
      }
      catch(ParserConfigurationException | SAXException ex) {
        AppMgr.logError(this, String.format("%s (%s)", AppMgr.getSharedResString(Constants.kErrorFile), Java.getStackTrace(ex, Constants.NL_SUBSTITUTE, 2, false)));
      }
    }

    input.close();
  }

  /**
   * IXmlRootMembers
   * Write out the plugin list as XML
   * @throws IOException
   */
  public void saveXml() throws IOException {
    FileOutputStream out = null;

    try {
      out = new FileOutputStream(tempSerializationFile);
      XML writer = new XML(out, comments);
      writer.startElement(kPluginsXmlTagName, xmlAtts);
      for(AxPlugIn plugIn : plugins.values()) {
        if(plugIn.isAutoLoad()) {
          plugIn.writeExternal(writer);
        }
      }

      writer.endElement(kPluginsXmlTagName);
      writer.close();
      serializationFile.delete();
      tempSerializationFile.renameTo(serializationFile);
    }
    catch(final IOException ex) {
      if(out != null) {
        try {
          out.close();
        }
        catch(final IOException x) {
          x.printStackTrace();
        }
      }

      tempSerializationFile.delete();

      // Rethrow original exception
      throw ex;
    }
  }

  /**
   * IXmlRootMembers
   * @return
   */
  public DefaultHandler getContentHandler() {
    return pluginHandler;
  }

  /**
   * IXmlRootMembers
   * @param writer
   */
  public void setWriter(XML writer) {
    
  }

  /**
   * IXmlRootMembers
   * @param entities
   */
  public void setEntities(Map entities) {
    
  }

  /**
   * IxErrorClient
   * Every log entry written by this object starts with a certain prefix, after timestamp, id, and thread tags.
   * @return 
   */
  public String errorEntryPrefix() {
    return kName;
  }

  /**
   * IxLogClient
   * Every log entry written by this object starts with a certain prefix, after timestamp, id, and thread tags.
   * @return 
   */
  public String logEntryPrefix() {
    return kName;
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
      Utils.list(plugins);
      pw.println(LogMgr.getDumpFooter(getClass()));

      return sw.toString();
    }
    else {
      return "";
    }
  }
}

////////////////////////////////////////////////////////////////////////////////
//                                 REVISION HISTORY                           //
// $Log$
////////////////////////////////////////////////////////////////////////////////
