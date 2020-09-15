/*
 * @(#)IxManager.java	 2/07/2010
 *
 * Copyright 1995-2020 Jeffrey Moss All Rights Reserved.
 *
 */

package com.jmoss;

/**
 * The base interface for all Manager objects.
 */
public interface IxManager  {

  /**
   * IxManager
   * @return
   */
  public boolean hasDAO();

  /**
   * IxManager
   * @return
   */
  public String getName();
}