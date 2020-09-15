/*
 * @(#)FullAppMgr.java	 12/20/2008
 *
 * Copyright 1995-2020 Jeffrey Moss All Rights Reserved.
 *
 */

package com.jmoss;

import com.jmoss.ui.IxMessageDialog;
import com.jmoss.ui.swing.FrameManager;
import com.jmoss.ui.swing.JxFrame;
import com.jmoss.util.ErrorManager;
import com.jmoss.util.ErrorModel;
import com.jmoss.util.IxErrorClient;
import com.jmoss.util.Java;
import com.jmoss.util.LogMgr;
import com.jmoss.util.ResourceUtilities;
import com.jmoss.util.Utils;

import java.awt.Image;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

import javax.swing.ImageIcon;

/**
 * Central manager for any thick client application that provides access to various
 * application-wide accessor methods.
 *
 * This class is specific to a Thick Client, and defines methods for retrieving
 * a frame manager, error and info popups, action-handling, and application exit.
 */
public final class FullAppMgr implements IxErrorClient {
  
  // This should point to the Application Class
  private static IxFullClientCallbacks application;
  
  private static final ResourceUtilities ru = new ResourceUtilities();
  private static final ResourceBundle   res = new Resources();
  
  private FrameManager frameManager;

  /** Dummy instance for callbacks */
  private static final FullAppMgr instance = new FullAppMgr();

  /** Default ctor  */
  private FullAppMgr() { 
  }
  
  /**
   * IxFullClientCallbacks
   * This is how the plugged in app class exposes itself to the package.
   * Only sets up resources.
   * This method is Idempotent.
   * @param client
   */
  public static void init(final IxFullClientCallbacks client) {
    application = client;
    ru.setResourcePackageName("com.jmoss.resources");
    
    AppMgr.init(client, FullAppMgr.class.getName());
  }

  /**
   * IxFullClientCallbacks
   * This is how the plugged in app class exposes itself to the package.
   * Sets up resources, logging, etc.
   * This method is Idempotent.
   * @param client
   * @param name
   */
  public static void init(final IxFullClientCallbacks client, final String name) {
    application = client;
    ru.setResourcePackageName("com.jmoss.resources");

    AppMgr.init(client, name);
  }

  /**
   * Set application by specifying the class table.
   * @param appclassname
   * @param name
   * @throws ClassNotFoundException
   * @throws IllegalAccessException
   * @throws InstantiationException
   */
  public static void init(final String appclassname, final String name) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
    if(application == null) {
      application = (IxFullClientCallbacks) Class.forName(appclassname).newInstance();
    }
    else {
      throw new InstantiationException("Already instantiated");
    }
    
    ru.setResourcePackageName("com.jmoss.resources");
    AppMgr.init(application, name);
  }

  /**
   * Exposes the instance so logging can be delegated here
   * @return 
   */
  public static FullAppMgr getInstance() { 
    return instance; 
  }

  /**
   * @param name
   * @return
   */
  public static Image getSharedImage(final String name) {
    return ru.getImage(name);
  }

  /**
   * @param name
   * @param size
   * @param type
   * @return
   */
  public static ImageIcon getSharedIcon(final String name, final int size, final int type) {
    LogMgr.putTrace(instance.getClass(), "getSharedIcon");
    ImageIcon icon;
    
    if(name.endsWith(Constants.kIconResSuffix)) {
      try {
        icon = ru.getImageIcon(res.getString(name), size, type);
      }
      catch(final MissingResourceException x) {
        ErrorManager.addError(instance, ErrorModel.createWarning(Java.getMessage(x), "Problem getting shared icon for "+name, null));
        icon = ru.getImageIcon("alert_en_US.gif", ResourceUtilities.kSmallIcon, ResourceUtilities.kNormalIcon);
      }
      catch(final NullPointerException x) {
        ErrorManager.addError(instance, ErrorModel.createWarning(Java.getMessage(x), "Problem getting shared icon for "+name, null));
        icon = ru.getImageIcon("alert_en_US.gif", ResourceUtilities.kSmallIcon, ResourceUtilities.kNormalIcon);
      }
    }
    else {
      //      ErrorManager.showError("Must call getSharedIcon with an icon ref:"+name, false);
      icon = ru.getImageIcon(name, ResourceUtilities.kSmallIcon, ResourceUtilities.kNormalIcon);
    }
    
    LogMgr.popTrace("getSharedIcon");
    return icon;
  }
  
  /**
   * @param name
   * @param size
   * @param type
   * @return
   */
  public static String getImageIconAbsolutePath(final String name, final int size, final int type) {
    return ru.getImageIconAbsolutePath(name, size, type);    
  }
  
  /**
   * @param name
   * @param size
   * @param type
   * @return
   */
  public static String getImageIconUrl(final String name, final int size, final int type) {
    return ru.getImageIconUrl(name, size, type);    
  }

  /**
   * @param name
   * @return
   */
  public static String getImageUrl(final String name) {
    return ru.getImageUrl(name);    
  }

  /**
   * Based on IxFullClientCallbacks
   */
  public static FrameManager getFrameManager() {
    if(instance.frameManager == null) {
      instance.frameManager = new FrameManager();
    }
    
    return instance.frameManager;
  }

  /**
   * Based on IxFullClientCallbacks
   * Allows CallBacks implementor to delegate to the application class.
   */
  public static JxFrame getCurrentFrame() {
    try {
      return application.getCurrentFrame();
    }
    catch(final Exception e) {
      // Infrequent case where application does not yet exist
      return null;
    }
  }

  /**
   * Based on IxFullClientCallbacks
   * Allows CallBacks implementor to delegate to the application class.
   */
  public static JxFrame getDefaultFrame() { 
    try {
      return application.getDefaultFrame(); 
    }
    catch(final Exception e) {
      // Infrequent case where application does not yet exist
      return null;
    }
  }

  /**
   * Based on IxFullClientCallbacks
   * Allows CallBacks implementor to delegate to the application class.
   * @return
   */
  public static IxMessageDialog getErrorDialog() { 
    try {
      return application.getErrorDialog(); 
    }
    catch(final Exception e) {
      // Infrequent case where application does not yet exist
      return null;
    }
  }

  /**
   * Based on IxFullClientCallbacks
   * Allows CallBacks implementor to delegate to the application class.
   * @return
   */
  public static IxMessageDialog getInfoDialog() { 
    try {
      return application.getInfoDialog(); 
    }
    catch(final Exception e) {
      // Infrequent case where application does not yet exist
      return null;
    }
  }
  
  /**
   * Based on IxFullClientCallbacks
   * Allows CallBacks implementor to delegate to the application class.
   */
  public static void performAction(String actioncommand) {
    application.performAction(actioncommand);
  }

  /**
   * Based on IxFullClientCallbacks
   * Allows CallBacks implementor to delegate to the application class.
   */
  public static void quit() {
    application.quit();
  }

  /**
   * IxLogClient
   * Every log entry written by this object starts with a certain prefix,
   * after timestamp, id, and thread tags.
   * @return 
   */
  public String logEntryPrefix() {
    return "Thick Application Manager";
  }

  /**
   * IxLogClient
   * Dump the contents of the object to a newline-delimited found.
   */
  public String dump(int verbose) {
    return "Thick Application Manager";
  }

  /**
   * IxErrorClient
   * Every error entry written by this object starts with a certain prefix
   * @return 
   */
  public String errorEntryPrefix() {
    return "Thick Application Manager";
  }
}
