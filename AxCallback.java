/*
 * @(#)AxCallback.java	 4/05/2003
 *
 * Copyright 1995-2020 Jeffrey Moss All Rights Reserved.
 *
 */

package com.jmoss;

import com.jmoss.data.UserManager;
import com.jmoss.util.CommandManager;
import com.jmoss.util.Preferences;
import com.jmoss.util.Properties;
import com.jmoss.util.ResourceUtilities;

import java.io.File;

import java.util.ResourceBundle;

/**
 *
 * @author  Jeffrey Moss
 */
public abstract class AxCallback implements IxClientCallbacks {

  protected CommandManager commandManager;
  protected UserManager userManager;
  
  protected String build = Constants.kDevelopment;
  protected int buildType = Constants.kTypeDevelopment;
  
  /**
   **/
  public AxCallback() {
  }
  
  /**
   * IxClientCallbacks
   * A general feedback sound
   * @param fileName
   */
  public void feedbackAudible(String fileName) {
  }

  /**
   * IxClientCallbacks
   * @return
   */
  public Properties getAppProperties() {
    return Preferences.getProps();
  }
  
  /**
   * IxClientCallbacks
   * @param base
   * @return
   */
  public String createApplicationFolder(String base) {
    return null;
  }

  /**
   * IxClientCallbacks
   * @return
   */
  public String getApplicationFolder() {
    return "";
  }
  
  /**
   * IxClientCallbacks
   * @return
   */
  public Integer getBugRevision() {
    return Constants.kRevision0;
  }
  
  /**
   * IxClientCallbacks
   * @return
   */
  public Integer getBuildType() {
    return Constants.kTypeDevelopment;
  }
  
  /**
   * IxClientCallbacks
   * @return
   */
  public Integer getClientType() {
    return Constants.kTypeJavaClient;
  }
  
  /**
   * IxClientCallbacks
   * @return
   */
  public String getBuildStamp() {
    return Constants.kBuildStamp;
  }
  
  /**
   * IxClientCallbacks
   * @return
   */
  public CommandManager getCommandManager() {
    return commandManager;
  }
  
  /**
   * IxClientCallbacks
   * @return
   */
  public Integer getMajorRevision() {
    return Constants.kRevision1;
  }
  
  /**
   * IxClientCallbacks
   * @return
   */
  public Integer getMicroRevision() {
    return Constants.kRevision0;
  }
  
  /**
   * IxClientCallbacks
   * @return
   */
  public Integer getMinorRevision() {
    return Constants.kRevision0;
  }
  
  /**
   * IxClientCallbacks
   * @return
   */
  public String getProductName() {
    return Constants.kProductName;
  }

  /**
   * IxClientCallbacks
   * @return
   */
  public String getProductFamilyName() {
    return getProductName();
  }

  /**
   * IxClientCallbacks
   * @return
   */
  public String getCompanyName() {
    return Constants.kCompanyName;
  }

  /**
   * IxClientCallbacks
   * @return
   */
  public String getCopyright() {
    return Constants.kCopyright;
  }

  /**
   * IxClientCallbacks
   * @return
   */
  public Preferences getPreferences() {
    return null;
  }

  /**
   * IxClientCallbacks
   * @return
   */
  public ResourceUtilities getPrivateResourceUtilities() {
    return null;
  }

  /**
   * IxClientCallbacks
   * @return
   */
  public ResourceBundle getPrivateResources() {
    return null;
  }
  
  /** 
   * IxClientCallbacks
   * Obtain the full path to the Temp directory folder. (Release)
   * @return
   */
  public String getTempPath() {
    String p = new File(".").getAbsolutePath();
    if(p.endsWith(".")) {
      return p.substring(0, p.length()-1);
    }
    else {
      return p;
    }
  }

  /**
   * IxClientCallBacks
   * AxCallback
   * @return
   */
  public UserManager getUserManager() {
    return userManager;
  }
  
  /**
   * IxClientCallBacks
   * AxCallback
   * This is how the plugged in app class exposes itself to the package
   * @param callback
   */
  public void init(IxClientCallbacks callback) {
  }
  
  /**
   * Message is same regardless of log level
   * @param msg
   */
  public void logError(String msg) {
  }
  
  /** Message depends on log level
   */
  public void logError(String verbose, String terse) {
  }
  
  /** Message is same regardless of log level
   */
  public void logEvent(String msg) {
  }
  
  /** Message depends on log level
   */
  public void logEvent(String verbose, String terse) {
  }

  /**
   * IxClientCallbacks
   * @param s
   * @return
   */
  public Integer getTargetType(final String s) {
    if(s.equals(Constants.kDev)) {
      return Constants.kTypeDevelopment;
    }
    else if(s.equals(Constants.kDevelopment)) {
      return Constants.kTypeDevelopment;
    }
    else if(s.equals(Constants.kDebug)) {
      return Constants.kTypeDebug;
    }
    else if(s.equals(Constants.kTest)) {
      return Constants.kTypeTest;
    }
    else if(s.equals(Constants.kStage)) {
      return Constants.kTypeStage;
    }
    else if(s.equals(Constants.kPreprod)) {
      return Constants.kTypePreproduction;
    }
    else if(s.equals(Constants.kPreproduction)) {
      return Constants.kTypePreproduction;
    }
    else if(s.equals(Constants.kProd)) {
      return Constants.kTypeProduction;
    }
    else if(s.equals(Constants.kProduction)) {
      return Constants.kTypeProduction;
    }
    else if(s.equals(Constants.kGA)) {
      return Constants.kTypeGA;
    }
    else {
      return Constants.kTypeDevelopment;
    }
  }

  /**
   * IxClientCallbacks
   * @param s
   * @return
   */
  public void setBuildType(final String s) {
    build = s;
    buildType = getTargetType(s);
  }

  /**
   * @return
   */
  public String getBuild() {
    return build;
  }
}
