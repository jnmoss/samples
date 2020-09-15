/*
 * @(#)IxClientCallbacks.java	 12/19/2008
 *
 * Copyright 1995-2020 Jeffrey Moss All Rights Reserved.
 *
 */

package com.jmoss;

import com.jmoss.data.UserManager;
import com.jmoss.util.CommandManager;
import com.jmoss.util.IxLogClient;
import com.jmoss.util.Preferences;
import com.jmoss.util.Properties;
import com.jmoss.util.ResourceUtilities;

import java.util.ResourceBundle;

/**
 * Any Client application must implement this or a sub-interface of this
 */
public interface IxClientCallbacks {
  
  /**
   * IxClientCallbacks
   * This is how the plugged in app class exposes itself to the package.
   * Only sets up resources.
   */
  public void init(IxClientCallbacks callback);

  /**
   * IxClientCallbacks
   * A general feedback sound
   */
  public void feedbackAudible(String fileName);

  /**
   * IxClientCallbacks
   * Located here to allow access to this method by applications that do not use an application-specific manager.
   *
   * Applications that do use a manager can simply delegate the implementation of this method to that manager.
   */
  public ResourceUtilities getPrivateResourceUtilities();

  /**
   * IxClientCallbacks
   * Located here to allow access to this method by applications that do not use an application-specific manager.
   *
   * Applications that do use a manager can simply delegate the implementation of this method to that manager.
   */
  public ResourceBundle getPrivateResources();

  /**
   * IxClientCallbacks
   */
  public Preferences getPreferences();

  /**
   * IxClientCallbacks
   */
  public Properties getAppProperties();

  /**
   * IxClientCallbacks
   * @param base
   * @return
   */
  public String createApplicationFolder(String base);

  /**
   * IxClientCallbacks
   */
  public String getApplicationFolder();

  /**
   * IxClientCallbacks
   * Return reference to an application-wide command manager.
   */
  public CommandManager getCommandManager();

  /**
   * IxClientCallbacks
   * Return reference to an application-wide user manager.
   */
  public UserManager getUserManager();

  /**
   * IxClientCallbacks
   * Log an error
   * @param client
   * @param msg
   */
  public void logError(IxLogClient client, String msg);

  /**
   * IxClientCallbacks
   * Log a status event
   * @param client
   * @param msg
   */
  public void logEvent(IxLogClient client, String msg);

  /**
   * IxClientCallbacks
   * Log a debug event
   * @param client
   * @param msg
   */
  public void logDebug(IxLogClient client, String msg);

  /**
   * IxClientCallbacks
   * Log a debug event
   * @param client
   * @param msg
   */
  public void logDebug(IxLogClient client, Throwable ex);

  /**
   * IxClientCallbacks
   * Log a stack trace
   * @param client
   * @param ex
   */
  public void logTrace(IxLogClient client, Throwable ex);

  /**
   * IxClientCallbacks
   * Log an object dump
   * @param client
   */
  public void logDump(IxLogClient client);

  /**
   * IxClientCallbacks
   * Log a newline-delimited list
   * @param list
   */
  public void logList(String list);

  /**
   * IxClientCallbacks
   * Obtain the full path to the Temp directory folder. (Release)
   * @return the directory folder path
   */
  public String getTempPath();

  /**
   * IxClientCallbacks
   * @return
   */
  public Integer getMajorRevision();

  /**
   * IxClientCallbacks
   * @return
   */
  public Integer getMinorRevision();

  /**
   * IxClientCallbacks
   * @return
   */
  public Integer getBugRevision();

  /**
   * IxClientCallbacks
   * @return
   */
  public Integer getMicroRevision();

  /**
   * IxClientCallbacks
   * @param s
   * @return
   */
  public void setBuildType(String s);

  /**
   * IxClientCallbacks
   * @return
   */
  public Integer getBuildType();

  /**
   * IxClientCallbacks
   * @return
   */
  public Integer getClientType();

  /**
   * IxClientCallbacks
   * @param s
   * @return
   */
  public Integer getTargetType(String s);

  /**
   * IxClientCallbacks
   * @return
   */
  public String getBuildStamp();

  /**
   * IxClientCallbacks
   * @return
   */
  public String getProductName();

  /**
   * IxClientCallbacks
   * @return
   */
  public String getProductFamilyName();

  /**
   * IxClientCallbacks
   * @return
   */
  public String getCompanyName();

  /**
   * IxClientCallbacks
   * @return
   */
  public String getCopyright();
}
