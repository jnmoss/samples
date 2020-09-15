/*
 * @(#)IxFullClientCallbacks.java	 10/31/2001
 *
 * Copyright 1995-2020 Jeffrey Moss All Rights Reserved.
 *
 */

package com.jmoss;

import com.jmoss.ui.IxMessageDialog;
import com.jmoss.ui.swing.FrameManager;
import com.jmoss.ui.swing.JxFrame;

/**
 * Thick Client application must implement this
 */
public interface IxFullClientCallbacks extends IxClientCallbacks {

  /**
   * Callbacks
   * This is how the plugged in app class exposes itself to the package.
   * Sets up resources, logging, etc.
   */
//  public void init(Callbacks callback, String appname);

  /**
   * IxFullClientCallbacks
   * Reference to an error dialog that implements the IxMessageDialog interface.
   */
  public IxMessageDialog getErrorDialog();

  /**
   * IxFullClientCallbacks
   * Reference to an informational dialog that implements the IxMessageDialog interface.
   */
  public IxMessageDialog getInfoDialog();

  /**
   * IxFullClientCallbacks
   * Reference to the host application/applet main frame.
   *
   * Located here to allow access to this method by applications
   * that do not use an application-specific manager.
   *
   * Applications that do use a manager can simply delegate the
   * implementation of this method to that manager.
   */
  public JxFrame getDefaultFrame();

  /**
   * IxFullClientCallbacks
   * Reference to the host application/applet filters/active frame.
   *
   * Located here to allow access to this method by applications
   * that do not use an application-specific manager.
   *
   * Applications that do use a manager can simply delegate the
   * implementation of this method to that manager.
   */
  public JxFrame getCurrentFrame();

  /**
   * IxFullClientCallbacks
   * Pop the filters/active frame from the stack.
   *
   * Located here to allow access to this method by applications
   * that do not use an application-specific manager.
   *
   * Applications that do use a manager can simply delegate the
   * implementation of this method to that manager.
   */
  public JxFrame popFrame();

  /**
   * IxFullClientCallbacks
   * Push the filters/active frame onto the stack.
   *
   * Located here to allow access to this method by applications
   * that do not use an application-specific manager.
   *
   * Applications that do use a manager can simply delegate the
   * implementation of this method to that manager.
   */
  public JxFrame pushFrame(JxFrame f);

  /**
   * IxFullClientCallbacks
   * Return reference to an application-wide frame manager.
   */
  public FrameManager getFrameManager();

  /**
   * IxFullClientCallbacks
   */
  public void performAction(String actioncommand);

  /**
   * IxFullClientCallbacks
   * Clean up.
   */
  public void quit();

}
