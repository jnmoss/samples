/*
 * @(#)MyServiceNowPlugIn.java	06/17/2017
 *
 * Copyright 2001-2020 Jeffrey Moss All Rights Reserved.
 *
 */

package com.jmoss.plugins;

import com.jmoss.AppMgr;
import com.jmoss.AxFullCallback;
import com.jmoss.Constants;
import com.jmoss.FullAppMgr;
import com.jmoss.data.CommonMutableTreeNode;
import com.jmoss.data.DAO;
import com.jmoss.data.LoginUser;
import com.jmoss.data.ServerDetails;
import com.jmoss.data.UserManager;
import com.jmoss.ui.PAO;
import com.jmoss.util.BiDiMap;
import com.jmoss.util.Converter;
import com.jmoss.util.ErrorManager;
import com.jmoss.util.ErrorModel;
import com.jmoss.util.IxErrorClient;
import com.jmoss.util.IxFormatter;
import com.jmoss.util.IxLogClient;
import com.jmoss.util.JSON;
import com.jmoss.util.Java;
import com.jmoss.util.Lists;
import com.jmoss.util.LogMgr;
import com.jmoss.util.Net;
import com.jmoss.util.Text;
import com.jmoss.util.Utils;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringWriter;
import java.io.Writer;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Vector;

import javax.swing.JMenuItem;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;

/**
 *
 * @author  Jeffrey Moss
 */
public final class MyServiceNowPlugIn extends AxPlugIn implements IxErrorClient, ActionListener {

  private static Object splunkPlugIn;
  private static boolean debug = false;

  private UserManager userManager;
  private LoginUser login;
  
  /**
   **/
  private BiDiMap istates = new BiDiMap();
  
  /**
   **/
  private BiDiMap tstates = new BiDiMap();
  
  private Class<?> UIFrame;
  private Class<?> UIFactory;

  private static final String INCIDENT = "Incident";
  private static final String TASK = "Task";

  private static final String SNOW_SCHEMA = "snow";

  private static final String SPLUNK_PROPS = "props";
  private static final String SPLUNK_CONFIG = "config";

  private static final String TA_COMMON = "my-ticketanalysis-common";
  private static final String TA_UI = "my-ticketanalysis-ui-common-new";
  private static final String TA_OWNER = "nobody";
  private static final String TA_SOURCETYPE = "snow_incidents";

  /**
   * https://docs.servicenow.com/bundle/jakarta-platform-user-interface/page/use/common-ui-elements/reference/r_OpAvailableFiltersQueries.html
   */
  private static final String AND = "^";
  private static final String OR = "^OR";
  private static final String NQ = "^NQ"; // All these conditions must be met [Short description][is empty] OR all these conditions must be met [Description][is not empty]

  private static final String STARTSWITH = "STARTSWITH";
  private static final String ENDSWITH = "ENDSWITH";
  private static final String CONTAINS = "LIKE";
  private static final String NOTCONTAINS = "NOTLIKE";
  private static final String ISEMPTY = "ISEMPTY";
  private static final String ISNOTEMPTY = "ISNOTEMPTY";
  private static final String BETWEEN = "BETWEEN";

  private static final String IN = "IN";
  private static final String NOTIN = "NOT IN";

  private static final String SAMEAS = "SAMEAS";
  private static final String NSAMEAS = "NSAMEAS";
  
  private static final String DISPLAY_TRUE = String.valueOf(true);
  private static final String DISPLAY_FALSE = String.valueOf(false);
  private static final String DISPLAY_ALL = "all";

  private static final String CONF_BATCH_SIZE = "snow_batchsize";
  private static final String CONF_DATE_FORMAT = "snow_dateformat";
  private static final String CONF_START_TIME = "snow_starttime";
  private static final String CONF_FIELD_ALIAS = "FIELDALIAS-datamodel";

  private static final String SYS_DATE_FORMAT = "glide.sys.date_format";
  private static final String SYS_TIME_FORMAT = "glide.sys.time_format";

  private static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd";
  private static final String DEFAULT_TIME_FORMAT = "HH:mm:ss";

  private static final String PROP_DATE_FORMAT  = MyServiceNowPlugIn.class.getSimpleName() + "." + CONF_DATE_FORMAT;
  private static final String PROP_START_TIME = MyServiceNowPlugIn.class.getSimpleName() + "." + CONF_START_TIME;
  private static final String PROP_FIELD_ALIAS = MyServiceNowPlugIn.class.getSimpleName() + "." + CONF_FIELD_ALIAS;
  
  /**
   **/
  public MyServiceNowPlugIn() {
    this(PAO.kTypeSwing, null);
  }
  
  /**
   * @param paotype
   */
  public MyServiceNowPlugIn(final int paotype) {
    this(paotype, null);
  }
  
  /**
   * @param paotype
   * @param userManager
   */
  public MyServiceNowPlugIn(final int paotype, final UserManager userManager) {
    LogMgr.putTrace(getClass(), "MyServiceNowPlugIn");
    
    name = "My ServiceNow Development Environment";
    description = "Access my ServiceNow data";
    
    istates.put("1", "New");
    istates.put("2", "In Progress");
    istates.put("3", "On Hold");
    istates.put("4", "Awaiting User Info");
    istates.put("6", "Resolved");
    istates.put("7", "Closed");
    istates.put("8", "Cancelled");

    tstates.put("1", "Open");
    tstates.put("1", "New");

    if(paotype == PAO.kTypeSwing) {
      try {
        UIFrame = ClassLoader.getSystemClassLoader().loadClass("com.jmoss.ui.swing.JxFrame");
        UIFactory = ClassLoader.getSystemClassLoader().loadClass("com.jmoss.ui.UIFactory");

        CommonMutableTreeNode n = new CommonMutableTreeNode("My ServiceNow");
        n.add(new CommonMutableTreeNode("Get Tickets", 0));
        n.add(new CommonMutableTreeNode("Get Tasks", 0));

        Method createMenu = UIFactory.getMethod("createMenu", CommonMutableTreeNode.class, ActionListener.class);
        theMenuItem = (JMenuItem) createMenu.invoke(UIFactory, n, this);

        // Mark the menus as plugin-related
        initializeMenus();
      }
      catch(ClassNotFoundException ex) {
        throw new UnsatisfiedLinkError(ex.getClass().getSimpleName() + IxFormatter.kNumberedColonDelimiter + "com.jmoss.ui.swing.JxFrame");
      }
      catch(InvocationTargetException ex) {
        throw new UnsatisfiedLinkError(ex.getTargetException().getClass().getSimpleName() + IxFormatter.kNumberedColonDelimiter + "com.jmoss.ui.UIFactory.createMenu");
      }
      catch(IllegalAccessException | NoSuchMethodException ex) {
        throw new UnsatisfiedLinkError(ex.getClass().getSimpleName() + IxFormatter.kNumberedColonDelimiter + "com.jmoss.ui.UIFactory.createMenu");
      }
    }

    this.userManager = userManager;

    LogMgr.popTrace("MyServiceNowPlugIn");
  }
  
  /**
   **/
  private boolean init() {
    if(login == null) {
      if(userManager == null) {
        userManager = UserManager.getInstance();
      }
      
      if(userManager != null) {
        login = userManager.getLoginBySchema(SNOW_SCHEMA);
        if(login == null) {
          try {
            Method getText = UIFactory.getMethod("getText", UIFrame, String.class, String.class, String.class, IxLogClient.class, String.class);
            Method getPassword = UIFactory.getMethod("getPassword", UIFrame, String.class, String.class, String.class, String.class, String.class, Boolean.class, String.class);

            String userName = (String) getText.invoke(UIFactory, null, "Connect to ServiceNow", "Enter User Name:", "", this, getClass().getName());
            // String userName = UIFactory.getText(null, "Connect to ServiceNow", "Enter User Name:", "", this, getClass().getName());
            if(userName != null) {
              String pw = (String) getPassword.invoke(UIFactory, null, "Connect to ServiceNow", "Enter Password:", "", this, getClass().getName());
              // String password = UIFactory.getText(null, "Connect to ServiceNow", "Enter Password:", "", this, getClass().getName());
              if(pw != null) {
                String host = (String) getText.invoke(UIFactory, null, "Connect to ServiceNow", "Enter Host:", "dev99999.service-now.com", this, getClass().getName());
                // String host = UIFactory.getText(null, "Connect to ServiceNow", "Enter Host:", "dev99999.service-now.com", this, getClass().getName());
                login = new LoginUser(userName, pw, new ServerDetails(host, SNOW_SCHEMA));
              }
            }
          }
          catch(Exception ex) {
            String trace = Java.getStackTrace(ex, Constants.NL_SUBSTITUTE, 2, false);
            AppMgr.logError(this, trace);
          }
        }
      }
    }
    
    if(splunkPlugIn == null) {
      Class<?> SplunkPlugIn = AppMgr.getPlugInManager().fetchClass("Splunk", "com.jmoss.plugins.SplunkPlugIn");
      Constructor<?> thector = null;
      Constructor<?>[] constructors = SplunkPlugIn.getConstructors();
      for(int i = 0; i < constructors.length; i++) {
        Constructor<?> ctor = constructors[i];
        Class<?>[] ptypes = ctor.getParameterTypes();
        if(ptypes.length == 2) {
          if(ptypes[0] == int.class && ptypes[1] == UserManager.class) {
            thector = ctor;
          }
        }
      }

      try {
        splunkPlugIn = thector.newInstance(PAO.kTypeHeadless, userManager);
      }
      catch(Exception ex) {
        String trace = Java.getStackTrace(ex, Constants.NL_SUBSTITUTE, 2, false);
        LogMgr.logError(this, trace);
      }
    }
    
    return login != null;
  }
  
  /**
   **/
  private boolean  register() {
    boolean ret = false;
    List<LoginUser> logins = userManager.getLoginsByType(DAO.kTypeHttps);

    try {
      Method showList = UIFactory.getMethod("showList", UIFrame, String.class, String.class, Collection.class, boolean.class, String.class);
      int sel = (Integer) showList.invoke(UIFactory, null, name, "Select Login", logins, true, true, getClass().getName());
      // int sel = UIFactory.showList(null, name, "Select Login", logins, true, true, getClass().getName());
      if(sel >= 0) {
        login = logins.get(sel);
        if(login != null) {
          ret = true;
        }
        else {
          try {
            Method getText = UIFactory.getMethod("getText", UIFrame, String.class, String.class, String.class, IxLogClient.class, String.class);
            Method getPassword = UIFactory.getMethod("getPassword", UIFrame, String.class, String.class, String.class, String.class, String.class, Boolean.class, String.class);

            String userName = (String) getText.invoke(UIFactory, null, "Connect to ServiceNow", "Enter User Name:", "", this, getClass().getName());
            // String userName = UIFactory.getText(null, "Connect to ServiceNow", "Enter User Name:", "", this, getClass().getName());
            if(userName != null) {
              String pw = (String) getPassword.invoke(UIFactory, null, "Connect to ServiceNow", "Enter Password:", "", this, getClass().getName());
              // String password = UIFactory.getText(null, "Connect to ServiceNow", "Enter Password:", "", this, getClass().getName());
              if(pw != null) {
                String host = (String) getText.invoke(UIFactory, null, "Connect to ServiceNow", "Enter Host:", "dev99999.service-now.com", this, getClass().getName());
                // String host = UIFactory.getText(null, "Connect to ServiceNow", "Enter Host:", "dev99999.service-now.com", this, getClass().getName());
                login = new LoginUser(userName, pw, new ServerDetails(host, ""));
                ret = true;
              }
            }
          }
          catch(Exception ex) {
            String trace = Java.getStackTrace(ex, Constants.NL_SUBSTITUTE, 2, false);
            AppMgr.logError(this, trace);
          }
        }
      }
    }
    catch(Exception ex) {
      String trace = Java.getStackTrace(ex, Constants.NL_SUBSTITUTE, 2, false);
      AppMgr.logError(this, trace);
    }
    
    return ret;
  }

  /**
   **/
  public void execute() {

  }
  
  /**
   * curl -k https://localhost:8089/services/auth/login -d username=admin -d password=pass
   * <response><sessionKey>...</sessionKey></response>
   */
  private JSONObject authenticate(final String host, final String userName, final String password, final int port, final boolean secure) throws IOException {
    CloseableHttpClient httpclient = null;

    if(secure) {
      CredentialsProvider credsProvider = new BasicCredentialsProvider();
      credsProvider.setCredentials(new AuthScope(new HttpHost(host)), new UsernamePasswordCredentials(userName, password));
      httpclient = HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();
    }
    else {
      Net.NonSSLHelper mySSLHelper = new Net.NonSSLHelper(); 
      httpclient = HttpClientBuilder.create().setSSLContext(mySSLHelper.createNonSSLContext(this)).setSSLHostnameVerifier(mySSLHelper.getPassiveX509HostnameVerifier()).build();
    }
    
    JSONObject ret = null;

    try {
      HttpPost post = new HttpPost(String.format("https://%s:%d/services/auth/login", host, port));
      List<NameValuePair> nvps = new ArrayList<NameValuePair>();
      nvps.add(new BasicNameValuePair("username",	userName));
      nvps.add(new BasicNameValuePair("password",  password));
      post.setEntity(new UrlEncodedFormEntity(nvps));
      
      CloseableHttpResponse response = httpclient.execute(post);
      try {
        String responseBody = EntityUtils.toString(response.getEntity());
        try {
          ret = new JSONObject(Converter.XMLtoJSON(responseBody));
        }
        catch(JSONException ex) {
          String trace = Java.getStackTrace(ex, Constants.NL_SUBSTITUTE, 2, false);
          LogMgr.logError(this, trace);
        }
      }
      catch(Exception ex) {
        String trace = Java.getStackTrace(ex, Constants.NL_SUBSTITUTE, 2, false);
        LogMgr.logError(this, trace);
      }
      finally {
        response.close();
      }
    }
    catch(Exception ex) {
      String trace = Java.getStackTrace(ex, Constants.NL_SUBSTITUTE, 2, false);
      LogMgr.logError(this, trace);
    }
    finally {
      try {
        httpclient.close();
      }
      catch(IOException ioe) {
        String trace = Java.getStackTrace(ioe, Constants.NL_SUBSTITUTE, 2, false);
        LogMgr.logError(this, trace);
      }
    }
    
    return ret;
  }
  
  /**
   "https://instance.service-now.com/api/now/table/incident" \
   --request POST \
   --header "Accept:application/json"\
   --header "Content-Type:application/json" \
   --data "{'short_description':'Unable to connect to office wifi','assignment_group':'287ebd7da9fe198100f92cc8d1d2154e','urgency':'2','impact':'2'}" \
   --user 'admin':'admin'  
   **/
  private JSONObject postIncident(final String category, final String subcategory, final String short_description, final String status, final String resolution) throws HttpException, IOException {
    CredentialsProvider credsProvider = new BasicCredentialsProvider();
    credsProvider.setCredentials(new AuthScope(new HttpHost(login.getServer().getName())), new UsernamePasswordCredentials(login.getUserName(), login.getPassword()));
    CloseableHttpClient httpclient = HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();

    JSONObject ret = null;

    try {
      HttpPost post = new HttpPost(String.format("%s/api/now/table/incident", login.getServer().getAddress()));
      post.setHeader("Accept", "application/json");
      post.setHeader("Content-Type", "application/json");

      try {
        JSONObject ejson = new JSONObject();
        ejson.put("category", category);
        ejson.put("subcategory", subcategory);
        ejson.put("short_description", short_description);
        Object fromRight = istates.getLeftFromRight(status);
        istates.resetRight(status);
        Integer state = Integer.valueOf(fromRight.toString());
        ejson.put("state", state.intValue());
        if(resolution != null) {
          ejson.put("close_notes", resolution);
        }
        
        HttpEntity entity = new StringEntity(ejson.toString());
        post.setEntity(entity);
      }
      catch(JSONException jsone) {
        ErrorManager.showException(this, jsone);
      }
      
      CloseableHttpResponse response = httpclient.execute(post);
      String responseBody = EntityUtils.toString(response.getEntity());
      try {
        ret = new JSONObject(responseBody);
        JSONObject rjson = ret.getJSONObject("result");
        int state = rjson.getInt("state");
        rjson.remove("state");

        Object fromLeft = istates.getRightFromLeft(String.valueOf(state));
        istates.resetLeft(String.valueOf(state));
        rjson.put("state", fromLeft.toString());
      }
      catch(JSONException e) {
        ErrorManager.showException(this, e);
      }
      finally {
        response.close();
      }
    }
    finally {
      httpclient.close();
    }
    
    return ret;
  }

  /**
   **/
  private JSONObject postTask(final String short_description, final String status, final String resolution) throws HttpException, IOException {
    CredentialsProvider credsProvider = new BasicCredentialsProvider();
    credsProvider.setCredentials(new AuthScope(new HttpHost(login.getServer().getName())), new UsernamePasswordCredentials(login.getUserName(), login.getPassword()));
    CloseableHttpClient httpclient = HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();

    JSONObject ret = null;;

    try {
      HttpPost post = new HttpPost(String.format("%s/api/now/table/task", login.getServer().getAddress()));
      post.setHeader("Accept", "application/json");
      post.setHeader("Content-Type", "application/json");

      try {
        JSONObject ejson = new JSONObject();
        ejson.put("short_description", short_description);
        Object fromRight = tstates.getLeftFromRight(status);
        tstates.resetRight(status);
        Integer state = Integer.valueOf(fromRight.toString());
        ejson.put("state", state.intValue());
        if(resolution != null) {
          ejson.put("close_notes", resolution);
        }
        
        HttpEntity entity = new StringEntity(ejson.toString());
        post.setEntity(entity);
      }
      catch(JSONException jsone) {
        ErrorManager.showException(this, jsone);
      }
      
      CloseableHttpResponse response = httpclient.execute(post);
      String responseBody = EntityUtils.toString(response.getEntity());
      try {
        ret = new JSONObject(responseBody);
        JSONObject rjson = ret.getJSONObject("result");
        int state = rjson.getInt("state");
        rjson.remove("state");

        Object fromLeft = tstates.getRightFromLeft(String.valueOf(state));
        tstates.resetLeft(String.valueOf(state));
        rjson.put("state", fromLeft.toString());
      }
      catch(JSONException e) {
        ErrorManager.showException(this, e);
      }
      finally {
        response.close();
      }
    }
    finally {
      httpclient.close();
    }
    
    return ret;
  }

  /**
   * @param spec
   * @return
   */
  public JSONObject add(final String spec) {
    JSONObject ret = null;

    try {
      JSONObject json = new JSONObject(spec);
      String description = json.getString("Description");
      String type = json.getString("Type");
      String status = json.optString("Status", "New");
      String resolution = json.optString("Resolution", null);
      if(status.equals("Resolved") || status.equals("Closed")) {
        if(type.equals(INCIDENT)) {
          ret = postIncident("Inquiry", "Internal Application", description, status, resolution);
        }
        else if(type.equals(TASK)) {
          ret = postTask(description, status, resolution);
        }
        else {
          throw new UnsupportedOperationException("Unsupported type: " + type);
        }
      }
      else {
        if(type.equals(INCIDENT)) {
          ret = postIncident("Inquiry", "Internal Application", String.format("%s %s %s", description, CommonMutableTreeNode.HDELIM, resolution), status, null);
        }
        else if(type.equals(TASK)) {
          ret = postTask(String.format("%s %s %s", description, CommonMutableTreeNode.HDELIM, resolution), status, null);
        }
        else {
          throw new UnsupportedOperationException("Unsupported type: " + type);
        }
      }
    }
    catch(Exception e) {
      ErrorManager.showException(this, e);
    }
    
    return ret;
  }
  
  public void delete(final String spec) {
    try {
      JSONObject json = new JSONObject(spec);
      String sys_id = json.getString("sys_id");
    }
    catch(JSONException jsone) {
      jsone.printStackTrace();
    }
  }
  
  public void get(final String spec) {
    try {
      JSONObject json = new JSONObject(spec);
      String sys_id = json.getString("sys_id");
    }
    catch(JSONException jsone) {
      jsone.printStackTrace();
    }
  }
  
  public void update(final String espec, final String nspec) {
    
  }
  
  private void get() {
    //"https://instance.service-now.com/api/now/table/incident/a9e30c7dc61122760116894de7bcc7bd" \
    //--request GET \
    //--header "Accept:application/json" \
    //--user 'username':'password'
  }
  
  private void put() {
    //"https://instance.service-now.com/api/now/table/incident/ef43c6d40a0a0b5700c77f9bf387afe3" \
    //--request PUT \
    //--header "Accept:application/json"\
    //--header "Content-Type:application/json" \
    //--data "{'assigned_to':'681b365ec0a80164000fb0b05854a0cd','urgency':'1','comments':'Elevating urgency, this is a blocking issue'}" \
    //--user 'admin':'admin'
  }
  
  /**
   * https://host:8089/services/properties/conf-file/config/key?value=val
   */
  private String setSplunkProperty(final String prop, final String val) {
    LogMgr.putTrace(getClass(), "setSplunkProperty");

    String responseBody = "";

    LoginUser loginUser = userManager.getLoginBySchema("en-US/app/launcher");
    if(loginUser != null) {
      String userName = loginUser.getUserName();
      String password = loginUser.getPassword();
      String host = loginUser.getServer().getName();
      int port = loginUser.getServer().getPort();

      CloseableHttpClient httpclient = null;
      
      try {
        JSONObject auth = authenticate(host, userName, password, port, false);
        JSONObject auth_response = auth.getJSONObject("response");
        String token = auth_response.get("sessionKey").toString().trim();

        Net.NonSSLHelper mySSLHelper = new Net.NonSSLHelper(); 
        httpclient = HttpClientBuilder.create().setSSLContext(mySSLHelper.createNonSSLContext(this)).setSSLHostnameVerifier(mySSLHelper.getPassiveX509HostnameVerifier()).build();

        String q = String.format("https://%s:%d/services/properties/my-ticketanalysis-common/config/%s/value=%s", host, port, prop, val);
        LogMgr.logEvent(this, q);
        
        HttpPost post = new HttpPost(q);
        post.setHeader("Accept", "application/json");
        post.setHeader("Content-Type", "application/json");
        post.setHeader("Authorization", String.format("Splunk %s", token));
        
        CloseableHttpResponse response = httpclient.execute(post);
        try {
          responseBody = EntityUtils.toString(response.getEntity());
        }
        finally {
          response.close();
        }
      }
      catch(Exception ex) {
        String trace = Java.getStackTrace(ex, Constants.NL_SUBSTITUTE, 2, false);
        LogMgr.logError(this, trace);
      }
      finally {
        try {
          httpclient.close();
        }
        catch(IOException ioe) {
          String trace = Java.getStackTrace(ioe, Constants.NL_SUBSTITUTE, 2, false);
          LogMgr.logError(this, trace);
        }
      }
    }
    
    LogMgr.popTrace("setSplunkProperty");
    return responseBody;
  }

  /**
   * https://host:8089/services/properties/conf-file/config/key
   * https://host:8089/servicesNS/nobody/my-ticketanalysis-ui-common-new/properties/my-ticketanalysis-common/config?output_mode=json
   */
  private String getSplunkProperty(final String key, final String stanza, final String prop) {
    LogMgr.putTrace(getClass(), "getSplunkProperty");

    String responseBody = "";
    boolean success = init();
    if(success) {
      if(splunkPlugIn != null) {
        try {
          Method getProperty = splunkPlugIn.getClass().getMethod("getProperty", String.class, String.class, String.class, String.class, String.class);
          responseBody = Utils.notNull(getProperty.invoke(splunkPlugIn, key, TA_UI, TA_OWNER, stanza, prop));
        }
        catch(Exception ex) {
          String trace = Java.getStackTrace(ex, Constants.NL_SUBSTITUTE, 2, false);
          LogMgr.logError(this, trace);
        }
      }
      else {
        LoginUser loginUser = userManager.getLoginBySchema("en-US/app/launcher");
        if(loginUser != null) {
          String userName = loginUser.getUserName();
          String password = loginUser.getPassword();
          String host = loginUser.getServer().getName();
          int port = loginUser.getServer().getPort();

          CloseableHttpClient httpclient = null;

          try {
            JSONObject auth = authenticate(host, userName, password, port, false);
            JSONObject auth_response = auth.getJSONObject("response");
            String token = auth_response.get("sessionKey").toString().trim();

            Net.NonSSLHelper mySSLHelper = new Net.NonSSLHelper();
            httpclient = HttpClientBuilder.create().setSSLContext(mySSLHelper.createNonSSLContext(this)).setSSLHostnameVerifier(mySSLHelper.getPassiveX509HostnameVerifier()).build();

            String q = String.format("https://%s:%d/servicesNS/nobody/my-ticketanalysis-ui-common-new/properties/my-ticketanalysis-common/config?output_mode=json", host, port);
            LogMgr.logEvent(this, AppMgr.getLogMgr().mkstr("{0}:{1}", q, prop));

            HttpGet get = new HttpGet(q);
            get.setHeader("Accept", "application/json");
            get.setHeader("Authorization", String.format("Splunk %s", token));

            CloseableHttpResponse response = httpclient.execute(get);
            try {
              responseBody = EntityUtils.toString(response.getEntity());
              JSONObject json = new JSONObject(responseBody);
              JSONArray array = json.getJSONArray("entry");
              for(int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                if(item.getString("name").equals(prop)) {
                  responseBody = item.getString(JSON.kContent);
                  break;
                }
              }
            }
            finally {
              response.close();
            }
          }
          catch(Exception ex) {
            String trace = Java.getStackTrace(ex, Constants.NL_SUBSTITUTE, 2, false);
            LogMgr.logError(this, trace);
          }
          finally {
            try {
              httpclient.close();
            }
            catch(IOException ioe) {
              String trace = Java.getStackTrace(ioe, Constants.NL_SUBSTITUTE, 2, false);
              LogMgr.logError(this, trace);
            }
          }
        }
      }
    }
    
    LogMgr.popTrace("getSplunkProperty");
    return responseBody;
  }

  /**
   * @param prop
   * @return
   */
  public String getProperty(final String prop) {
    LogMgr.putTrace(getClass(), "getProperty");

    String responseBody = "";
    boolean success = init();
    if(success) {
      CredentialsProvider credsProvider = new BasicCredentialsProvider();
      credsProvider.setCredentials(new AuthScope(new HttpHost(login.getServer().getName())), new UsernamePasswordCredentials(login.getUserName(), login.getPassword()));
      CloseableHttpClient httpclient = HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();

      try {
        String q = String.format("%s/api/now/table/sys_properties?sysparm_fields=value,name&name=%s", login.getServer().getAddress(), prop);
        LogMgr.logEvent(this, q);

        HttpGet get = new HttpGet(q);
        get.setHeader("Accept", "application/json");

        CloseableHttpResponse response = httpclient.execute(get);
        try {
          responseBody = EntityUtils.toString(response.getEntity());
          JSONObject json = new JSONObject(responseBody);
          try {
            JSONArray array = json.getJSONArray("result");
            for(int i = 0; i < array.length(); i++) {
              JSONObject item = array.getJSONObject(i);
              if(item.getString("name").equals(prop)) {
                responseBody = item.getString("value");
                break;
              }
            }
          }
          catch(JSONException jsone) {
            if(prop.equals(SYS_DATE_FORMAT)) {
              responseBody = DEFAULT_DATE_FORMAT;
            }
            else if(prop.equals(SYS_TIME_FORMAT)) {
              responseBody = DEFAULT_TIME_FORMAT;
            }
          }
        }
        finally {
          response.close();
        }
      }
      catch(Exception ex) {
        String trace = Java.getStackTrace(ex, Constants.NL_SUBSTITUTE, 2, false);
        LogMgr.logEvent(this, trace);
      }
      finally {
        try {
          httpclient.close();
        }
        catch(IOException ioe) {
          String trace = Java.getStackTrace(ioe, Constants.NL_SUBSTITUTE, 2, false);
          LogMgr.logEvent(this, trace);
        }
      }
    }
    
    LogMgr.popTrace("getProperty");
    return responseBody;
  }
  
  /**
   * Filter by state:
   * https://%s/api/now/table/incident?sysparm_display_value=%s&sysparm_limit=%d&sysparm_query=ORDERBYsys_updated_on&sysparm_query=stateIN6,7
   */
  private JSONArray getIncidents(final String display_value) {
    LogMgr.putTrace(getClass(), "getIncidents");

    JSONArray results = new JSONArray();

    String start = AppMgr.getProperty(Constants.kPreference, PROP_START_TIME);
    if(start == null) {
      start = getSplunkProperty(TA_COMMON, SPLUNK_CONFIG, CONF_START_TIME);
    }

    CredentialsProvider credsProvider = new BasicCredentialsProvider();
    credsProvider.setCredentials(new AuthScope(new HttpHost(login.getServer().getName())), new UsernamePasswordCredentials(login.getUserName(), login.getPassword()));
    CloseableHttpClient httpclient = HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();
    CloseableHttpResponse response = null;
        
    String startdate = Text.leftOfFromStart(start, " ", 1, start);
    String starttime = Text.rightOf(start, " ", 1, start);
    int batchsize = Integer.valueOf(getSplunkProperty(TA_COMMON, SPLUNK_CONFIG, CONF_BATCH_SIZE));
    int count = batchsize;
    int offset = 0;

    String sys_date_format = getProperty(SYS_DATE_FORMAT);
    String sys_time_format = getProperty(SYS_TIME_FORMAT);
    String sys_timestamp_format = String.format("%s %s", sys_date_format, sys_time_format);    
    SimpleDateFormat timestampFormat = new SimpleDateFormat(sys_timestamp_format, Locale.US);
    String newstart = timestampFormat.format(new Date());

    try {
      while(count == batchsize) {
        String params = String.format("sysparm_display_value=%s&sysparm_offset=%d&sysparm_limit=%s&sysparm_query=ORDERBYsys_updated_on%ssys_updated_on%sjavascript:gs.dateGenerate('%s','%s')", display_value, offset, batchsize, Net.encodeURL("^"), Net.encodeURL(">"), startdate, starttime);
        String q = String.format("%s/api/now/table/incident?%s", login.getServer().getAddress(), params);
        LogMgr.logEvent(this, q);
        
        HttpGet get = new HttpGet(q);
        get.setHeader("Accept", "application/json");
        response = httpclient.execute(get);
        String s = EntityUtils.toString(response.getEntity());
        JSONObject json = new JSONObject(s);
        JSONArray array = json.getJSONArray("result");
        
        offset += batchsize;
        count = array.length();
        
        for(int i = 0; i < count; i++) {
          results.put(array.get(i));
        }
      }

      AppMgr.putProperty(PROP_START_TIME, newstart);
    }
    catch(Exception ex) {
      String trace = Java.getStackTrace(ex, Constants.NL_SUBSTITUTE, 2, false);
      ErrorManager.queue(this, this, trace);
    }
    finally {
      if(response != null) {
        try {
          response.close();
        }
        catch(IOException ioe) {
          String trace = Java.getStackTrace(ioe, Constants.NL_SUBSTITUTE, 2, false);
          ErrorManager.queue(this, this, trace);
        }
      }
      
      try {
        httpclient.close();
      }
      catch(IOException ioe) {
        String trace = Java.getStackTrace(ioe, Constants.NL_SUBSTITUTE, 2, false);
        ErrorManager.queue(this, this, trace);
      }
    }
    
    LogMgr.popTrace("getIncidents");
    return results;
  }

  /**
   * "https://localhost:8089/services/data/inputs/oneshot?name=MyFile&host=MyHost&index=MyIndex&sourcetype=MySourcetype"
   * MyFile = "C:/Program Files/Splunk/etc/apps/my-ticketanalysis-ui-common-new/local/MyServiceNowPlugIn/Data/my_tickets.json"
   */
  private JSONObject upload(final String file, final String appname, final String index, final String sourcetype) throws HttpException, IOException {
    JSONObject ret = null;

    LoginUser loginUser = userManager.getLoginBySchema("en-US/app/launcher");
    if(loginUser != null) {
      String userName = loginUser.getUserName();
      String password = loginUser.getPassword();
      String host = loginUser.getServer().getName();
      int port = loginUser.getServer().getPort();

      CloseableHttpClient httpclient = null;
      
      try {
        JSONObject auth = authenticate(host, userName, password, port, false);
        JSONObject auth_response = auth.getJSONObject("response");
        String token = auth_response.get("sessionKey").toString().trim();

        Net.NonSSLHelper mySSLHelper = new Net.NonSSLHelper(); 
        httpclient = HttpClientBuilder.create().setSSLContext(mySSLHelper.createNonSSLContext(this)).setSSLHostnameVerifier(mySSLHelper.getPassiveX509HostnameVerifier()).build();

        String url = String.format("https://%s:%d/servicesNS/nobody/%s/data/inputs/oneshot", host, port, appname);
        HttpPost post = new HttpPost(url);
        post.setHeader("Accept", "application/json");
        post.setHeader("Content-Type", "application/json");
        post.setHeader("Authorization", String.format("Splunk %s", token));

        List<NameValuePair> nvps = new ArrayList<NameValuePair>();
        nvps.add(new BasicNameValuePair("name", file));
        nvps.add(new BasicNameValuePair("host",  Net.getHostName()));
        nvps.add(new BasicNameValuePair("index", index));
        nvps.add(new BasicNameValuePair("sourcetype", sourcetype));
        nvps.add(new BasicNameValuePair("output_mode", "json"));
        post.setEntity(new UrlEncodedFormEntity(nvps));

        CloseableHttpResponse response = httpclient.execute(post);
        String responseBody = EntityUtils.toString(response.getEntity());
        try {
          ret = new JSONObject(responseBody);
          debug = true;
        }
        catch(JSONException e) {
          ErrorManager.showException(this, e);
        }
        finally {
          response.close();
        }
      }
      catch(Exception ex) {
        debug = true;
      }
      finally {
        httpclient.close();
      }
    }
    
    return ret;
  }

  /**
   **/
  public void actionPerformed(final ActionEvent e) {
    if(e.getActionCommand() == "Get Tickets") {
      init();
      boolean registered = register();
      if(registered) {
        String display_value = DISPLAY_TRUE;
        JSONArray array = getIncidents(display_value);
        try {
          Vector<Vector<String>> rows = new Vector<>();
          ErrorManager.setSyncPoint(this);
          for(int i = 0; i < array.length(); i++) {
            try {
              Vector<String> row = new Vector<>();
              JSONObject object = (JSONObject) array.get(i);

              String sys_id = object.get("sys_id").toString().trim();
              row.add(sys_id);
              String number = object.get("number").toString().trim();
              row.add(number);
              String updated = object.get("sys_updated_on").toString().trim();
              row.add(updated);
              String sys_class_name = object.get("sys_class_name").toString().trim();
              row.add(sys_class_name);
              String category = object.get("category").toString().trim();
              row.add(category);
              String subcategory = object.get("subcategory").toString().trim();
              row.add(subcategory);

              String u_application_name = Utils.notNull(object.opt("u_application_name"));
              row.add(u_application_name);

              try {
                String cmdb_ci = display_value.equals(DISPLAY_TRUE) ? object.getJSONObject("cmdb_ci").getString("display_value") : object.get("cmdb_ci").toString().trim();
                row.add(cmdb_ci);
              }
              catch(JSONException jsone) {
                row.add(object.get("cmdb_ci").toString().trim());
              }

              try {
                String assignment_group = display_value.equals(DISPLAY_TRUE) ? object.getJSONObject("assignment_group").getString("display_value") : object.get("assignment_group").toString().trim();
                row.add(assignment_group);
              }
              catch(JSONException jsone) {
                row.add(object.get("assignment_group").toString().trim());
              }

              String short_description = object.get("short_description").toString().trim();
              row.add(short_description);
              String description = object.get("description").toString().trim();
              row.add(description);
              String severity = object.get("severity").toString().trim();
              row.add(severity);
              String state = object.get("state").toString().trim();
              if(display_value.equals(DISPLAY_TRUE)) {
                row.add(state);
              }
              else {
                try {
                  Object fromLeft = istates.getRightFromLeft(state);
                  istates.resetLeft(state);
                  row.add(fromLeft.toString());
                }
                catch(Exception ex) {
                  row.add(state);
                }
              }

              try {
                String company = display_value.equals(DISPLAY_TRUE) ? object.getJSONObject("company").getString("display_value") : object.get("company").toString().trim();
                row.add(company);
              }
              catch(JSONException jsone) {
                row.add(object.get("company").toString().trim());
              }

              String contact_type = object.get("contact_type").toString().trim();
              row.add(contact_type);
              String work_notes = object.get("work_notes").toString().trim();
              row.add(work_notes);
              String close_notes = object.get("close_notes").toString().trim();
              row.add(close_notes);
              //u_atr="{"template":null,"mlData":[{"key":"automation_type","source":"servicenow","predictions":null,"userValue":null},{"key":"work_queue","source":"servicenow","predictions":null,"userValue":null},{"key":"ticket_management","source":"servicenow","predictions":null,"userValue":null},{"key":"","source":"servicenow","predictions":null,"userValue":null},{"key":"u_knowledge","source":"servicenow","predictions":null,"userValue":null},{"key":"daystaken","source":"servicenow","predictions":null,"userValue":null},{"key":"solution_id","source":"servicenow","predictions":null,"userValue":null}],"confidenceLevel":0.9366666666666666,"sourceServiceName":"ATHOS","sourceJobName":"A1","automation":false,"updateDate":1505698902000,"continuous":true,"properties":{}}"

              rows.add(new Vector<String>(Utils.escapeDelimited(row, ",")));
            }
            catch(JSONException jsone) {
              ErrorManager.queue(this, this, jsone.getMessage());
            }
          }

          Collection<ErrorModel> errors = ErrorManager.getSyncErrors(this);
          if(Utils.isNullOrEmpty(errors) == false) {
            ErrorManager.showErrors(this, null, "Get Tickets", "Get Tickets", errors);
          }

          ErrorManager.clearSyncPoint(this);

          Vector<String> columns = Utils.toVector("SysID", "Number", "Updated", "Type", "Category", "Subcategory", "Application", "CMDB-CI", "Assignment Group", "Short Description", "Description", "Severity", "State", "Company", "Contact", "Work Notes", "Close Notes");
          try {
            Method showTable = UIFactory.getMethod("showTable", UIFrame, String.class, String.class, Vector.class, Vector.class, String.class);
            showTable.invoke(UIFactory, null, host + "/api/now/table/incident", String.valueOf(rows.size()), columns, rows, getClass().getName());
            // UIFactory.showTable(null, host + "/api/now/table/incident", String.valueOf(rows.size()), columns, rows, getClass().getName());
          }
          catch(Exception ex) {
            try {
              Utils.writeDelimited(getClass().getSimpleName() + " " + host + "/api/now/table/incident", Lists.concat(columns, rows), Utils.NL, false);
            }
            catch(IOException ioe) {
              String trace = Java.getStackTrace(ioe, Constants.NL_SUBSTITUTE, 2, false);
              AppMgr.logError(this, trace);
            }
          }

          String path = AppMgr.getApplicationFolder() + File.separator + DAO.kData + File.separator + String.format("%s-incident.csv", host);
          rows.add(0, columns);
          Utils.writeDelimited(path, rows, ",", false);
        }
        catch(Exception ex) {
          ErrorManager.showError(this, Text.ellipsize(array.toString(), 128, Constants.INSERTION.BISECT), ex.getMessage(), true);
        }
      }
    }
    else if(e.getActionCommand() == "Get Tasks") {
      boolean registered = register();
      if(registered) {
        String display_value = DISPLAY_TRUE;
        int limit = 5000;
        
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(new AuthScope(new HttpHost(login.getServer().getName())), new UsernamePasswordCredentials(login.getUserName(), login.getPassword()));
        CloseableHttpClient httpclient = HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();
        try {
          HttpGet get = new HttpGet(String.format("%s/api/now/table/task?sysparm_display_value=%s&sysparm_limit=%d", login.getServer().getAddress(), display_value, limit));
          get.setHeader("Accept", "application/json");
          
          CloseableHttpResponse response = null;
          try {
            response = httpclient.execute(get);
            String responseBody = EntityUtils.toString(response.getEntity());
            JSONObject json = null;
            try {
              json = new JSONObject(responseBody);
              JSONArray array = json.getJSONArray("result");
              Vector<Vector<String>> rows = new Vector<>();
              for(int i = 0; i < array.length(); i++) {
                Vector<String> row = new Vector<>();
                JSONObject object = (JSONObject) array.get(i);
                String sys_id = object.get("sys_id").toString().trim();
                row.add(sys_id);
                String number = object.get("number").toString().trim();
                row.add(number);
                String updated = object.get("sys_updated_on").toString().trim();
                row.add(updated);
                String sys_class_name = object.get("sys_class_name").toString().trim();
                row.add(sys_class_name);
                try {
                  String assignment_group = display_value.equals(DISPLAY_TRUE) ? object.getJSONObject("assignment_group").getString("display_value") : object.get("assignment_group").toString().trim();
                  row.add(assignment_group);
                }
                catch(JSONException jsone) {
                  row.add(object.get("assignment_group").toString().trim());
                }
                
                String short_description = object.get("short_description").toString().trim();
                row.add(short_description);
                String description = object.get("description").toString().trim();
                row.add(description);
                String state = object.get("state").toString().trim();
                if(display_value.equals(DISPLAY_TRUE)) {
                  row.add(state);
                }
                else {
                  try {
                    Object fromLeft = istates.getRightFromLeft(state);
                    istates.resetLeft(state);
                    row.add(fromLeft.toString());
                  }
                  catch(Exception ex) {
                    row.add(state);
                  }
                }

                try {
                  String company = display_value.equals(DISPLAY_TRUE) ? object.getJSONObject("company").getString("display_value") : object.get("company").toString().trim();
                  row.add(company);
                }
                catch(JSONException jsone) {
                  row.add(object.get("company").toString().trim());
                }
                
                String contact_type = object.get("contact_type").toString().trim();
                row.add(contact_type);
                String work_notes= object.get("work_notes").toString().trim();
                row.add(work_notes);
                String close_notes = object.get("close_notes").toString().trim();
                row.add(close_notes);
                //u_atr="{"template":null,"mlData":[{"key":"automation_type","source":"servicenow","predictions":null,"userValue":null},{"key":"work_queue","source":"servicenow","predictions":null,"userValue":null},{"key":"ticket_management","source":"servicenow","predictions":null,"userValue":null},{"key":"","source":"servicenow","predictions":null,"userValue":null},{"key":"u_knowledge","source":"servicenow","predictions":null,"userValue":null},{"key":"daystaken","source":"servicenow","predictions":null,"userValue":null},{"key":"solution_id","source":"servicenow","predictions":null,"userValue":null}],"confidenceLevel":0.9366666666666666,"sourceServiceName":"ATHOS","sourceJobName":"A1","automation":false,"updateDate":1505698902000,"continuous":true,"properties":{}}"

                rows.add(new Vector<String>(Utils.escapeDelimited(row, ",")));
              }
              
              Vector<String> columns = Utils.toVector("SysID", "Number", "Updated", "Type", "Assignment Group", "Short Description", "Description", "State", "Company", "Contact", "Work Notes", "Close Notes");
              try {
                Method showTable = UIFactory.getMethod("showTable", UIFrame, String.class, String.class, Vector.class, Vector.class, String.class);
                showTable.invoke(UIFactory, null, login.getServer().getName() + "/api/now/table/task", String.valueOf(rows.size()), columns, rows, getClass().getName());
                // UIFactory.showTable(null, login.getServer().getName() + "/api/now/table/task", String.valueOf(rows.size()), columns, rows, getClass().getName());
              }
              catch(Exception ex) {
                try {
                  Utils.writeDelimited(getClass().getSimpleName() + " " + "Apps", Lists.concat(Utils.toVector("Name", "Description", "Version"), rows), Utils.NL, false);
                }
                catch(IOException ioe) {
                  String trace = Java.getStackTrace(ioe, Constants.NL_SUBSTITUTE, 2, false);
                  AppMgr.logError(this, trace);
                }
              }

              String path = AppMgr.getApplicationFolder() + File.separator + DAO.kData + File.separator + String.format("%s-task.csv", login.getServer().getName());
              rows.add(0, columns);
              Utils.writeDelimited(path, rows, ",", false);
            }
            catch(JSONException ex) {
              ErrorManager.showError(this, Text.ellipsize(responseBody, 128, Constants.INSERTION.BISECT), ex.getMessage(), true);
            }
          }
          catch(Exception ex) {
            ErrorManager.showException(this, ex);
          }
          finally {
            try {
              response.close();
            }
            catch(IOException ioe) {
              ErrorManager.showException(this, ioe);
            }
          }
        }
        finally {
          try {
            httpclient.close();
          }
          catch(IOException ioe) {
            ErrorManager.showException(this, ioe);
          }
        }
      }
    }
  }
  
  /**
   * IxCommand
   * @return
   */
  public boolean undoable() {
    return false;
  }
  
  /**
   * IxLogClient
   * Every log entry written by this object starts with a certain prefix after timestamp, id, and thread tags.
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
  private static final class MyCallBack extends AxFullCallback {
    
    private String appname = MyServiceNowPlugIn.class.getSimpleName();
    private String appfolder = new File("").getAbsolutePath();

    public MyCallBack() {
      FullAppMgr.init(this, appname);
    }

    /**
     * AxCallback
     * IxFullClientCallbacks
     * @return
     */
    @Override
    public String getProductName() {
      return appname;
    }

    @Override
    public String getApplicationFolder() {
      return appfolder;
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
  
  /**
   **/
  private JSONObject enrich(final JSONObject in) {
    String field_alias = AppMgr.getProperty(Constants.kPreference, PROP_FIELD_ALIAS);
    String[] a = JSONObject.getNames(in);
    List<String> names = Arrays.asList(a);
    List<String> tokens = Text.tokenize(field_alias, IxFormatter.kGroupDelimiterStr, null);
    for(String token : tokens) {
      String name = Text.leftOfFromStart(token, "=", 1, token).trim();
      if(names.contains(name)) {
        try {
          Object object = in.get(name);
          if(object != null) {
            if(object instanceof String) {
              String v = object.toString();
              if(v.length() == 0 || v.equalsIgnoreCase("null")) {
                String value = Text.dequote(Text.rightOf(token, "=", 1, token).trim(), false);
                if(JSON.isJSON(value)) {
                  in.put(name, new JSONObject(value));
                }
                else {
                  in.put(name, value);
                }
              }
            }
          }
          else {
            throw new IllegalArgumentException("No value for " + name);
          }
        }
        catch(JSONException jsone) {
          ErrorManager.queue(this, this, jsone.getMessage());
        }
      }
      else {
        try {
          String value = Text.rightOf(token, "=", 1, token).trim();
          Java.IFTHEN ifThen = Java.getIFTHEN(value);
          if(ifThen != null) {
            String condition = ifThen.getCondition();
            List<String> ops = Text.tokenize(condition, "==", condition);
            if(ops.get(0) != condition) {
              Object val = in.opt(ops.get(0));
              if(val != null) {
                if(val.toString().equals(ops.get(1))) {
                  in.put(name, Text.dequote(ifThen.getResult1(), false));
                }
                else {
                  in.put(name, Text.dequote(ifThen.getResult2(), false));
                }
              }
              else {
                throw new IllegalArgumentException("No value for " + ops.get(0));
              }
            }
            else {
              in.put(name, Text.dequote(value, false));
            }
          }
          else {
            in.put(name, Text.dequote(value, false));
          }
        }
        catch(JSONException jsone) {
          ErrorManager.queue(this, this, jsone.getMessage());
        }
      }
    }

    return in;
  }

  /**
   * true|false (display_value)
   * upload MyCSV
   * download true|false (display_value) MyCSV
   * "C:\Program Files\Splunk\etc\apps\my-ticketanalysis-ui-common-new\local\MyServiceNowPlugIn\Data\my_tickets.csv"
   * @param args
   */
  public static void main(String[] args) {
    int ret = 0;
    PrintStream out = System.out;
    MyCallBack cb = new MyCallBack();
    MyServiceNowPlugIn instance = null;

    if(debug == false) {
      System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
    }

    try {
      instance = new MyServiceNowPlugIn(PAO.kTypeHeadless, UserManager.getUserManager(PAO.kTypeHeadless));
      LogMgr.putTrace(instance.getClass(), "main");
      
      JSONArray array = null;
      String fs = "";
      if(args[0].equals("download")) {
        array = instance.getIncidents(args[1]);
        LogMgr.logEvent(instance, AppMgr.getLogMgr().mkstr("Received {0} results in {1} bytes", String.valueOf(array.length()), String.valueOf(array.toString().length())));

        File path = new File(args[2]);
        if(path.isAbsolute()) {
          File parent = path.getParentFile();
          if(parent.exists() == false) {
            Utils.createDirectory(parent);
          }

          fs = path.getAbsolutePath();
        }
        else {
          path = new File(AppMgr.getApplicationFolder() + File.separator + DAO.kData);
          if(path.exists() == false) {
            Utils.createDirectory(path);
          }

          fs = path.getAbsolutePath() + File.separator + args[2];
        }
        
        JSON.writeDelimited(fs, array, ",", false);
        LogMgr.logEvent(instance, AppMgr.getLogMgr().mkstr("Wrote to file {0}", fs));
      }
      else {
        if(args[0].equals("upload")) {
          String jfile = "";
          if(Utils.isJsonFile(args[1])) {
            jfile = args[1];
          }
          else {
            Writer w = Converter.csvToJSON(instance, new File(args[1]));

            jfile = Utils.changeExtension(args[1], "json");
            String s = w.toString();
            array = new JSONArray(s);
            LogMgr.logEvent(instance, AppMgr.getLogMgr().mkstr("Received {0} results in {1} bytes", String.valueOf(array.length()), String.valueOf(array.toString().length())));

            StringWriter sw = new StringWriter();
            JSONWriter jw = new JSONWriter(sw);
            jw.array();
            for(int i = 0; i < array.length(); i++) {
              jw.object();
              JSONObject object = array.getJSONObject(i);
              JSONObject enriched = instance.enrich(object);
              String[] names = JSONObject.getNames(enriched);
              for(int j = 0; j < names.length; j++) {
                jw.key(names[j]).value(enriched.get(names[j]));
              }
              jw.endObject();
              sw.append(Utils.NL);
            }
            jw.endArray();

            String text = Text.truncate(sw.toString().substring(1), 1);
            text = text.replace(Utils.NL + ",", Utils.NL);
            Utils.writeFile(jfile, text, false);
          }          

          String appname = instance.getSplunkProperty(TA_COMMON, SPLUNK_CONFIG, "app_name");
          String index = instance.getSplunkProperty(TA_COMMON, SPLUNK_CONFIG, "index_name");
          String sourcetype = instance.getSplunkProperty(TA_COMMON, SPLUNK_CONFIG, "sourcetype");
          JSONObject theUpload = instance.upload(jfile, appname, index, sourcetype);
          LogMgr.logEvent(instance, AppMgr.getLogMgr().mkstr("{0}", theUpload));
        }
        else {
          array = instance.getIncidents(args[0]);
          LogMgr.logEvent(instance, AppMgr.getLogMgr().mkstr("Received {0} results in {1} bytes", String.valueOf(array.length()), String.valueOf(array.toString().length())));
          PrintStream stream = args.length > 1 ? new PrintStream(new FileOutputStream(args[1], false)) : out;
          
          for(int i = 0; i < array.length(); i++) {
            JSONObject object = array.getJSONObject(i);
            JSONObject enriched = instance.enrich(object);
            byte[] bytes = enriched.toString().getBytes("UTF-8");
            stream.println(new String(bytes));
          }
        }

        try {
          AppMgr.store(instance);
        }
        catch(IOException ioe) {
          String trace = Java.getStackTrace(ioe, Constants.NL_SUBSTITUTE, 2, false);
          LogMgr.logError(instance, trace);
        }
      }

      ret = 0;
    }
    catch(Exception e) {
      e.printStackTrace();
      ret = 1;
      System.exit(1);
    }
    finally {
      System.err.flush();
      System.out.flush();
      LogMgr.logEvent(instance, "Exit: " + String.valueOf(ret));
      LogMgr.popTrace("main");
    }
  }
}
