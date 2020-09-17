/*
 * @(#)UserManager.java	 01/14/2003
 *
 * Copyright 1995-2020 Jeffrey Moss All Rights Reserved.
 *
 */

package com.jmoss.data;

import com.jmoss.AppMgr;
import com.jmoss.AxMutableManager;
import com.jmoss.Constants;
import com.jmoss.IxManager;
import com.jmoss.IxMutableManager;
import com.jmoss.Passthrough;
import com.jmoss.event.IxActiveUserListener;
import com.jmoss.event.IxUserListener;
import com.jmoss.ui.IxUserManagerPAO;
import com.jmoss.ui.P10nMgr;
import com.jmoss.ui.PAO;
import com.jmoss.ui.PAOFactory;
import com.jmoss.ui.Settings;
import com.jmoss.util.Command;
import com.jmoss.util.ErrorManager;
import com.jmoss.util.ErrorModel;
import com.jmoss.util.IxErrorClient;
import com.jmoss.util.Java;
import com.jmoss.util.LogMgr;
import com.jmoss.util.Net;
import com.jmoss.util.Sets;
import com.jmoss.util.Text;
import com.jmoss.util.Utils;

import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;
import java.beans.VetoableChangeSupport;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;

import java.net.URL;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

/**
 * TODO: Add the SecurityManager as a VetoableChangeListener?
 * TODO: Handle non-XML data source
 *
 * The User Manager manages multiple users (and TODO groups) and manages load() and save() operations on them as a group.
 * It also maintains integrity by enforcing uniqueness.
 * The RegisteredUser Object knows how to load() or save() itself in each supported DAO.
 * The RegisteredUser Object is responsible for managing field requirements and field-level validation.
 * The DAO for the persistence chosen provides access to that data store and knows whether to create a connection and which namespace or schema to use.
 * Each DAO instance knows which methods to call in the RegisteredUser instance to correctly persist the object.
 * The User Manager uses the single-connection DAO model - users are accessed from a single data store
 *
 * As a Model, this class:
 * Encapsulates application state
 * Responds to state queries - via accessor methods
 * Exposes application functionality
 *
 * @version $Revision$
 * Last Updated By: $Author$
 * Last Updated On: $Date$
 */

 public final class UserManager extends AxMutableManager implements IxErrorClient, IxPersistent {

  public static final String kBase = "User";
  
  static final String kName = "User Manager";

  /** List of users */
  private final Vector<RegisteredUser> users = new Vector<>();
  
  /** User status change history */
  private List<StatusChange> history;

  /** UserListener List*/
  private final List<IxUserListener> userListeners = new Vector<>();
  
  /** UserListener List*/
  private final List<IxActiveUserListener> activeUserListeners = new Vector<>();
  
  /** The Data Access Object for User Manager */
  private transient UserManagerDAO theDAO;
  
  /** The Presentation Access Object for User Manager */
  private transient IxUserManagerPAO thePAO;
  
  private transient VetoableChangeSupport vetoableChangeSupport = new VetoableChangeSupport(this);
  
  private String host;
  
  private int maxUsers = Integer.MAX_VALUE;
  
  private boolean replaceIfExists = false;
  
  private static final String kReconstituted = "Re-constituted User";
  private static final String kNoUser = "Undeterminable User";
  
  // TODO: change to DAO appropriate for multiple users of WKC
  /** Default Data Access Object type for User Manager */
  private static final int kDefaultDAO = DAO.kTypeXML;

  /** Default Presentation Access Object type for User Manager */
  private static final int kDefaultPAO = PAO.kTypeSwing;
  
  private static RegisteredUser defaultUser;

  private static UserManager instance;

  /**
   * @throws ClassNotFoundException
   * @throws IllegalAccessException
   * @throws InstantiationException
   * @throws DAOException
   */
  public UserManager() throws ClassNotFoundException, IllegalAccessException, InstantiationException, DAOException {
    getUserManager(kDefaultPAO);
  }

  /**
   * @param datasource
   * @param uiType
   * @throws ClassNotFoundException
   * @throws IllegalAccessException
   * @throws InstantiationException
   * @throws DAOException
   */
  public UserManager(final String datasource, final int uiType) throws ClassNotFoundException, IllegalAccessException, InstantiationException, DAOException {
    LogMgr.putTrace(getClass(), "UserManager");

    AppMgr.logEvent(this, AppMgr.getLogMgr().mkstr("Retrieving daoType from property {0}", Settings.concat("data.dao.type", kName)));
    int daotype = Integer.valueOf(AppMgr.getProperty(Constants.kPreference, Settings.concat("data.dao.type", kName), String.valueOf(kDefaultDAO))).intValue();
    
    AppMgr.logEvent(this, AppMgr.getLogMgr().mkstr("Retrieving host from property {0}", Settings.concat("data.dao.host", kName)));
    host = AppMgr.getProperty(Constants.kPreference, Settings.concat("data.dao.host", kName), ConnectionFactory.kDefaultServer);
    
    AppMgr.logEvent(this, AppMgr.getLogMgr().mkstr("Retrieving users from data source {0}/{1}.{2}", host, datasource, String.valueOf(daotype)));

    // Set the instance as early as possible to allow other objects (like a DAO) to help build the UserManager, being that they are called from the ctor
    instance = this;

    DAOFactory daoFactory = DAOFactory.getDAOFactory(daotype, host, datasource, -1, "", "", false);
    if(daoFactory != null) {
      AppMgr.logEvent(this, "DAOFactory Ok");

      // Register this component as a user of the DAO type
      AppMgr.registerDaoClient(toString(), daotype);
      
      // Register the factory for Users
      AppMgr.registerDaoFactory(kName, daoFactory);
      
      // Set the DAO
      theDAO = daoFactory.getUserManagerDAO();
      AppMgr.logEvent(this, "theDAO Ok");

      checkHistory();
    }
    else {
      AppMgr.logError(this, "No DAOFactory");
      throw new IllegalArgumentException(DAO.kUnknown);
    }
    
    AppMgr.logEvent(this, AppMgr.getLogMgr().mkstr("Retrieving paotype from property {0}", Settings.concat("ui.pao.type", kName)));
    int paotype = Integer.valueOf(AppMgr.getProperty(Constants.kPreference, Settings.concat("ui.pao.type", kName), String.valueOf(uiType))).intValue();
    
    PAOFactory paoFactory = PAOFactory.getPAOFactory(paotype);
    if(paoFactory != null) {
      AppMgr.logEvent(this, "PAOFactory Ok");
      thePAO = paoFactory.getUserManagerPAO(kName);
    }
    else {
      AppMgr.logError(this, "No PAOFactory");
      throw new IllegalArgumentException(PAO.kUnknown);
    }
    
    populateSupportedTypes();

    LogMgr.popTrace("UserManager");
  }
  
  /**
   * @return
   * @see java.lang.Object
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
   * Accessor
   * @return 
   */
  public List<RegisteredUser> getUsers() {
    LogMgr.putTrace(getClass(), "getUsers");

    if(users == null || users.size() == 0) {
      try {
        AppMgr.logEvent(this, "No users in this instance. Calling initialization to load");

        UserManager instance = getUserManager(PAO.kTypeSwing);
        
        //
        instance.load();
        
        LogMgr.popTrace("getUsers");
        return instance.users;
      }
      catch(final Exception e) {
        AppMgr.logDebug(this, e, true);
      }
    }
    
    LogMgr.popTrace("getUsers");
    return users;
  }

  /**
   * Accessor - Indirect
   * @return 
   */
  public int getUserCount() {
    return users.size();
  }

  /**
   * Accessor
   * Get the Default User
   * @return
   */
  public RegisteredUser getUser() {
    // TODO: return only if autoLogin
    if(users.size() == 1) {
      return users.firstElement();
    }
    else {
      return thePAO.getUser();
    }
  }

  /**
   * @param _user
   * @return
   */
  public RegisteredUser getUser(final RegisteredUser _user) {
    for(RegisteredUser user:users) {
      if(user.equals(_user)) {
        return user;
      }
    }

    return null;
  }

  /**
   * @param clientId
   * @return
   */
  @Passthrough
  public RegisteredUser getUser(final String clientId) {
    return thePAO.getUser(clientId);
  }
  
  /**
   * @param clientId
   * @param schema
   * @return
   */
  @Passthrough
  public RegisteredUser getUser(final String clientId, final String schema) {
    return thePAO.getUser(schema, clientId);
  }

  /**
   * @return
   */
  public static RegisteredUser getDefaultUser() {
    if(defaultUser == null) {
      defaultUser = new RegisteredUser(Utils.kUserName, Net.getLocalHostNameUrl(), Net.getLocalHost());
      defaultUser.addLoginUser(new LoginUser());
    }

    return defaultUser;
  }
  
  /**
   * @param name
   * @return
   */
  public RegisteredUser getUserByName(final String name) {
    for(RegisteredUser user : users) {
      if(user.toString().equals(name)) {
        return user;
      }
    }

    return null;
  }

  /**
   * @param con
   * @return
   */
  public RegisteredUser getUserByConcatenation(final String con) {
    String name = Text.leftOfFromEnd(con, "@", 1, "");
    String comp = Text.rightOf(con, "@", 1, "");
    for(RegisteredUser user : users) {
      if(user.getUserName().equals(name)) {
        if(user.getServer().getComponent().equals(comp)) {
          return user;
        }
      }
    }

    return null;
  }

  /**
   * @param name
   * @param component
   * @return
   */
  public RegisteredUser getUserById(final String name, final String component) {
    for(RegisteredUser user : users) {
      if(user.getUserName().equals(name) && user.getServer().getComponent().equals(component)) {
        return user;
      }
    }

    return null;
  }

  /**
   * Find the one active user.
   * @return
   */
  public RegisteredUser getActiveUser() {
    LogMgr.putTrace(getClass(), "getActiveUser");

    for(RegisteredUser user : users) {
      if(user.getStatus().equals(Account.kActive)) {
        AppMgr.logEvent(this, AppMgr.getLogMgr().mkstr("Returning user ({0})", user));
        LogMgr.popTrace("getActiveUser");
        return user;
      }
    }

    RegisteredUser user = createUnknownUser();
    AppMgr.logEvent(this, AppMgr.getLogMgr().mkstr("No active user out of {0}. Returning user ({1})", String.valueOf(users.size()), user));
    LogMgr.popTrace("getActiveUser");
    return user;
  }
  
  /**
   * Find login for the specified component under the active user.
   * @param component
   * @return
   */
  public LoginUser getLoginByComponent(final String component) {
    LogMgr.putTrace(getClass(), "getLoginByComponent");
    AppMgr.logEvent(this, AppMgr.getLogMgr().mkstr("Retrieving login for component ({0})", component));

    for(RegisteredUser user : users) {
      if(user.getStatus().equals(Account.kActive)) {
        if(user.getLoginUsers() != null) {
          for(LoginUser login : user.getLoginUsers()) {
            if(login.getServer().getComponent().equals(component) && login.getStatus().equals(Account.kActive)) {
              AppMgr.logEvent(this, AppMgr.getLogMgr().mkstr("Returning login ({0})", login));
              LogMgr.popTrace("getLoginByComponent");
              return login;
            }
          }
        }
      }
    }

    AppMgr.logEvent(this, "Returning null login");
    LogMgr.popTrace("getLoginByComponent");
    return null;
  }
  
  /**
   * Find login for the specified ID under the active user.
   * @param id
   * @return
   */
  public LoginUser getLoginByID(final String id) {
    LogMgr.putTrace(getClass(), "getLoginByID");
    AppMgr.logEvent(this, AppMgr.getLogMgr().mkstr("Retrieving login for ID ({0})", id));

    for(RegisteredUser user : users) {
      if(user.getStatus().equals(Account.kActive)) {
        if(user.getLoginUsers() != null) {
          for(LoginUser login : user.getLoginUsers()) {
            if(login.getUserName().equals(id) && login.getStatus().equals(Account.kActive)) {
              AppMgr.logEvent(this, AppMgr.getLogMgr().mkstr("Returning login ({0})", login));
              LogMgr.popTrace("getLoginByID");
              return login;
            }
          }
        }
      }
    }

    AppMgr.logEvent(this, "Returning null login");
    LogMgr.popTrace("getLoginByID");
    return null;
  }
  
  /**
   * Find login for the specified name under the active user.
   * @param name
   * @return
   */
  public LoginUser getLoginByName(final String name) {
    LogMgr.putTrace(getClass(), "getLoginByName");
    AppMgr.logEvent(this, AppMgr.getLogMgr().mkstr("Retrieving login for name ({0})", name));

    for(RegisteredUser user : users) {
      if(user.getStatus().equals(Account.kActive)) {
        if(user.getLoginUsers() != null) {
          for(LoginUser login : user.getLoginUsers()) {
            if(login.getUserName().equals(name) && login.getStatus().equals(Account.kActive)) {
              AppMgr.logEvent(this, AppMgr.getLogMgr().mkstr("Returning login ({0})", login));
              LogMgr.popTrace("getLoginByName");
              return login;
            }
          }
        }
      }
    }

    AppMgr.logEvent(this, "Returning null login");
    LogMgr.popTrace("getLoginByName");
    return null;
  }

  /**
   * Find login for the specified concatenated name (X@Y) under the active user.
   * @param con
   * @return
   */
  public LoginUser getLoginByConcatenation(final String con) {
    LogMgr.putTrace(getClass(), "getLoginByConcatenation");
    AppMgr.logEvent(this, AppMgr.getLogMgr().mkstr("Retrieving login for ({0})", con));
    String name = Text.leftOfFromEnd(con, "@", 1, "");
    String comp = Text.rightOf(con, "@", 1, "");

    for(RegisteredUser user : users) {
      if(user.getStatus().equals(Account.kActive)) {
        if(user.getLoginUsers() != null) {
          for(LoginUser login : user.getLoginUsers()) {
            if(login.getServer().getComponent().equals(comp) && login.getUserName().equals(name)) {
              AppMgr.logEvent(this, AppMgr.getLogMgr().mkstr("Returning login ({0})", login));
              LogMgr.popTrace("getLoginByConcatenation");
              return login;
            }
          }
        }
      }
    }

    AppMgr.logEvent(this, "Returning null login");
    LogMgr.popTrace("getLoginByConcatenation");
    return null;
  }

  /**
   * Find login for the specified schema under the active user.
   * @param schema
   * @return
   */
  public LoginUser getLoginBySchema(final String schema) {
    LogMgr.putTrace(getClass(), "getLoginBySchema");
    AppMgr.logEvent(this, AppMgr.getLogMgr().mkstr("Retrieving login for schema ({0})", schema));

    for(RegisteredUser user : users) {
      if(user.getStatus().equals(Account.kActive)) {
        if(user.getLoginUsers() != null) {
          for(LoginUser login : user.getLoginUsers()) {
            if(login.getServer().getComponent().equals(schema) && login.getStatus().equals(Account.kActive)) {
              AppMgr.logEvent(this, AppMgr.getLogMgr().mkstr("Returning login ({0})", login));
              LogMgr.popTrace("getLoginBySchema");
              return login;
            }
          }
        }
      }
    }

    AppMgr.logEvent(this, "Returning null login");
    LogMgr.popTrace("getLoginBySchema");
    return null;
  }

  /**
   * @param name
   * @return
   */
  public List<LoginUser> getLoginsByServer(final String name) {
    List<LoginUser> logins = new ArrayList<>();

    for(RegisteredUser user : users) {
      if(user.getStatus().equals(Account.kActive)) {
        if(user.getLoginUsers() != null) {
          for(LoginUser login : user.getLoginUsers()) {
            if(login.getServer().getName().equals(name) && login.getStatus().equals(Account.kActive)) {
              logins.add(login);
            }
          }
        }
      }
    }

    return logins;
  }

  /**
   * @param type
   * @return
   */
  public List<LoginUser> getLoginsByType(final int type) {
    List<LoginUser> logins = new ArrayList<>();

    for(RegisteredUser user : users) {
      if(user.getStatus().equals(Account.kActive)) {
        if(user.getLoginUsers() != null) {
          for(LoginUser login : user.getLoginUsers()) {
            if(login.getServer().getType() == type && login.getStatus().equals(Account.kActive)) {
              logins.add(login);
            }
          }
        }
      }
    }

    return logins;
  }

  /**
   * Find anonymous user for the specified identifier.
   * @param s
   * @return
   */
  public AnonymousUser getAnonymous(final String s) {
    LogMgr.putTrace(getClass(), "getAnonymous");
    AppMgr.logEvent(this, AppMgr.getLogMgr().mkstr("Retrieving anonymous user for schema ({0})...", s));

    for(RegisteredUser user : users) {
      if(user.getStatus().equals(Account.kActive)) {
        if(user.getAnonymousUsers() != null) {
          for(AnonymousUser au : user.getAnonymousUsers()) {
            if(au.getDescription().equals(s)) {
              AppMgr.logEvent(this, AppMgr.getLogMgr().mkstr("Returning anonymous user ({0})...", au));
              LogMgr.popTrace("getAnonymous");
              return au;
            }
          }
        }
      }
    }

    AppMgr.logEvent(this, "Returning null anonymous user");
    LogMgr.popTrace("getAnonymous");
    return null;
  }

  /**
   * @param user
   * @return
   */
  public boolean isDefaultUser(final RegisteredUser user) {
    return user.equals(getDefaultUser());
  }

  /**
   * @param user
   * @return
   */
  public boolean isUnknownUser(final RegisteredUser user) {
    return user.toString().equals(AppMgr.getSharedResString(Constants.kUnknownUser));
  }

  /**
   * Controllers call this to cause all listeners to be notified of the change.
   * The DAO is called to persist the new user data.
   * @param _user
   * @param doSave
   * @return
   */
  public boolean addUser(final RegisteredUser _user, boolean doSave) {
    if(users.size() == maxUsers) {
      throw new IllegalStateException(String.format("The maximum number of users has been reached (%d)", maxUsers));
    }

    if(replaceIfExists == false && users.contains(_user)) {
      AppMgr.logEvent(this, String.format("Did not add user (%s) because it already exists and Replace-if-Exists=false", _user));
      return false;
    }
    
    if(_user.getTheDAO() == null) {
      _user.setTheDAO(theDAO.userDAO);
    }
    
    boolean added = users.add(_user);

    if(doSave) {
      try {
        theDAO.insert(_user);
        
        // Write the changes out immediately
        theDAO.save();
      }
      catch(final Exception e) {
        AppMgr.logError(this, String.format("Error (%s) adding user (%s)", Java.getStackTrace(e, false), _user));
        //ErrorManager.addError(this, String.format("Error (%s) adding user (%s)", Java.getStackTrace(e, Constants.NL_SUBSTITUTE, 2, false), _user));
        return false;
      }
    }

    fireUserAdded(_user);
    return added;
  }

  /**
   * The DAO is called to persist the new user data.
   * @param name
   * @return
   * @throws Exception
   */
  public boolean addActiveUser(final String name) throws Exception {
    if(users.size() == maxUsers) {
      throw new IllegalStateException(String.format("The maximum number of users has been reached (%d)", maxUsers));
    }

    RegisteredUser aUser = createActiveUser(name);
    if(users.contains(aUser)) {
      return false;
    }
    
    users.add(aUser);
    theDAO.insert(aUser);

    fireUserAdded(aUser);
    return true;
  }

  /**
   * Controllers call this to cause all listeners to be notified of the change.
   * The DAO is called to persist the new user data.
   * @param name
   * @return
   * @throws Exception
   */
  public boolean addInactiveUser(final String name) throws Exception {
    if(users.size() == maxUsers) {
      throw new IllegalStateException(String.format("The maximum number of users has been reached (%d)", maxUsers));
    }

    RegisteredUser aUser = createInactiveUser(name);
    if(users.contains(aUser)) {
      return false;
    }
    
    users.add(aUser);
    theDAO.insert(aUser);

    fireUserAdded(aUser);
    return true;
  }

  /**
   * Controllers call this to cause all listeners to be notified of the change.
   * The DAO is called to persist the updated user data. 
   * @param existingUser
   * @param newUser
   * @return
   * @throws Exception
   */
  public boolean updateUser(final RegisteredUser existingUser, final RegisteredUser newUser) throws Exception {  
    boolean removed = users.remove(existingUser);
    if(removed) {
      users.add(newUser);
  
      // Have the DAO update the user data
      if(theDAO.update(existingUser, newUser)) {
        newUser.setUpdated(new Date());

        // Write the changes out immediately
        theDAO.save();
    
        fireUserChanged(existingUser, newUser);
        return true;
      }
      else {
        return false;
      }
    }
    else {
      return false;
    }
  }

  /**
   * @param user
   * @return
   */
  private boolean exists(final RegisteredUser user) {
    return users.contains(user);
  }

  /**
   * Controllers call this to cause all listeners to be notified of the change.
   * The DAO is called to persist the deleted user data.
   * @param user
   * @return
   * @throws Exception
   */
  public boolean removeUser(final RegisteredUser user) throws Exception {
    if(user == getActiveUser()) {
      throw new IllegalStateException("Active User may not be deleted");
    }
    
    boolean removed = users.remove(user);

    if(removed) {
      theDAO.delete(user);
      
      // Write the changes out immediately
      theDAO.save();
  
      fireUserRemoved(user);
      return true;
    }
    else {
      return false;
    }
  }

  /**
   * Controllers call this to cause all listeners to be notified of the change.
   * The DAO is called to persist the deleted user data.
   * @param users
   * @return
   * @throws Exception
   */
  public boolean removeUsers(final Collection<RegisteredUser> users) throws Exception {
    if(users.contains(getActiveUser())) {
      throw new IllegalStateException("Active User may not be deleted");
    }
    
    boolean removed = this.users.removeAll(users);

    if(removed) {
      for(RegisteredUser aUser:users) {
        theDAO.delete(aUser);
      }
  
      fireUsersRemoved(users);
      return true;
    }
    else {
      return false;
    }
  }

  /**
   **/
  public void clearHistory() {
    if(history != null) {
      history.clear();
    }
  }

  /**
   * @param he
   * @return
   */
  public boolean addHistory(final StatusChange he) {
    if(history == null) {
      history = new ArrayList<StatusChange>();
    }
    
    if(history.contains(he) == false) {
      return history.add(he);
    }
    else {
      return false;
    }
  }

  /**
   * @return
   */
  public List<StatusChange> getUserHistory() {
    List<StatusChange> uh = new ArrayList<StatusChange>();
    
    if(history != null) {
      for(StatusChange he:history) {
        if(he.getContext().equals("User")) {
          uh.add(he);
        }
      }
    }
    
    return uh;
  }
  
  /**
   * @return
   */
  public List<StatusChange> getServerHistory() {
    List<StatusChange> changes = new ArrayList<StatusChange>();
    
    if(history != null) {
      for(StatusChange he:history) {
        if(he.getContext().equals("Server")) {
          changes.add(he);
        }
      }
    }
    
    return changes;
  }

  /**
   * Fulfill role of the Model
   * Invoke a controller object to manage user input
   * @param cmd
   * @return
   */
  @Passthrough
  public boolean create(final Command cmd) {
    clear();
    boolean b = thePAO.create(cmd);
    
    // This is where to getDependents the new RegisteredUser and ask whether to switch to that new user
    
    return b;
  }

  /**
   * Fulfill role of the Model
   * Invoke a controller object to manage user input
   * @param idx
   * @param cmd
   * @return
   */
  @Passthrough
  public boolean edit(final int idx, final Command cmd) {
    return thePAO.edit(idx, cmd);
  }

  /**
   * Fulfill role of the Model
   * Invoke a controller object to manage user input
   * @param user
   * @param cmd
   * @return
   */
  @Passthrough
  public boolean  edit(final RegisteredUser user, final Command cmd) {
    return thePAO.edit(user, cmd);
  }

  /**
   * @param clientId
   */
  @Passthrough
  public void showUsers(final String clientId) {
    thePAO.showUsers(clientId);
  }

  /**
   * @param schema
   * @param clientId
   */
  @Passthrough
  public void showUsers(final String schema, final String clientId) {
    thePAO.showUsers(schema, clientId);
  }

  /**
   * @param currentUser
   * @param clientId
   */
  public RegisteredUser switchUsers(final RegisteredUser currentUser, final String clientId) {
    RegisteredUser newUser = thePAO.switchUsers(currentUser, clientId);

    if(newUser != null && newUser != currentUser) {
      currentUser.setStatus(Account.kInactive);
      currentUser.setOnline(false);

      newUser.setStatus(Account.kActive);
      newUser.setOnline(true);

      // Write the changes out immediately
      boolean saved = save();
      if(saved) {
        fireActiveUserChanged(newUser);
        return newUser;
      }
      else {
        return currentUser;
      }
    }
    else {
      return currentUser;
    }
  }
  
  /**
   * Fulfill role of the Model by registering views for future change notifications
   * @param listener
   */
  public void addUserListener(IxUserListener listener) {
    if(userListeners.contains(listener) == false) {
      userListeners.add(listener);
    }
  }

  /**
   * Fulfill role of the Model by un-registering views for future change notifications
   * @param listener
   */
  public void removeUserListener(IxUserListener listener) {
    userListeners.remove(listener);
  }

  /**
   * Fulfill role of the Model by notifying views of change
   */
  private void fireUserAdded(RegisteredUser user) {
    for(int i = userListeners.size() - 1; i >= 0; i--) {
      userListeners.get(i).userAdded(user);
    }
  }

  /**
   * Fulfill role of the Model by notifying views of change
   */
  private void fireUserRemoved(RegisteredUser user) {
    for(int i = userListeners.size() - 1; i >= 0; i--) {
      userListeners.get(i).userRemoved(user);
    }
  }

  /**
   * Fulfill role of the Model by notifying views of change
   */
  private void fireUsersRemoved(Collection users) {
    for(int i = userListeners.size() - 1; i >= 0; i--) {
      userListeners.get(i).usersRemoved(users);
    }
  }

  /**
   * Fulfill role of the Model by notifying Views of change
   */
  private void fireUserChanged(RegisteredUser existingUser, RegisteredUser newUser) {
    for(int i = userListeners.size() - 1; i >= 0; i--) {
      userListeners.get(i).userChanged(existingUser, newUser);
    }
  }

  /**
   * Fulfill role of the Model by registering views for future change notifications
   * @param listener
   */
  public void addActiveUserListener(IxActiveUserListener listener) {
    if(activeUserListeners.contains(listener) == false) {
      activeUserListeners.add(listener);
    }
  }

  /**
   * Fulfill role of the Model by un-registering views for future change notifications
   * @param listener
   */
  public void removeUserListener(IxActiveUserListener listener) {
    activeUserListeners.remove(listener);
  }

  /**
   * Fulfill role of the Model by notifying views of change
   */
  private void fireActiveUserChanged(final RegisteredUser user) {
    for(int i = activeUserListeners.size() - 1; i >= 0; i--) {
      activeUserListeners.get(i).activeUserChanged(user);
    }
  }

  /**
   **/
  private void checkHistory() {
    if(theDAO.isIncludeHistory()) {
      // If any user has a status not represented in the history, create a history entry now
      Set<RegisteredUser> updated = new HashSet<>();
      for(RegisteredUser aUser:users) {
        for(StatusChange he:history) {
          if(he.getID().equals(aUser.getId())) {
            if(he.getNewStatus().equals(aUser.getStatus())) {
              updated.add(aUser);
              break;
            }
          }
        }
      }
      
      if(updated.size() < users.size()) {
        // Some users are not up to date, so create history entries for them
        Collection<RegisteredUser> outofdate = Utils.leftOuterDifference(users, updated, false);
        for(RegisteredUser aUser:outofdate) {
          // TODO: getLastStatus
          Object lastStatus = Account.kActive;
          history.add(new StatusChange(aUser.getId(), "User", lastStatus, aUser.getStatus(), "The status was changed by another component", new Date()));
        }
        
        // Ensure these new history entries are persisted
        theDAO.save();
      }
    }
  }

  /**
   * IxPersistent
   */
  public void clear() {
    users.clear();
  }

  /**
   * IxPersistent
   * This method exists in the DAOs which are called when the DAO instances are instantiated.
   * This method is called during DAO-to-DAO operations like copy, move, replicate, etc.
   * @return
   */
  public boolean load() {
    Object loaded = theDAO.load();
    if(loaded == null) {
      ErrorManager.addError(this, ErrorModel.createNoteworthyError("No data", String.format("Could not load any data for (%s)", theDAO.getName()), ErrorModel.kActionNotify));
      return false;
    }
    
    checkHistory();
    
    return true;
  }

  /**
   * IxPersistent
   * @return
   */
  public boolean save() {
    String instance = Constants.kEmptyString;

    // Use populate instead of save to catch the list of users and the history list
    try {
      // Save a copy for rollback
      instance = theDAO.replicate();

      theDAO.populate();

      // No longer need the copy
      new File(instance).delete();
      return true;
    }
    catch(final Exception e) {
      ErrorManager.addError(this, String.format("Error (%s) populating data [Original saved to %s]", Java.getStackTrace(e, Constants.NL_SUBSTITUTE, 2, false), instance));
      return false;
    }
  }

  /**
   * This static method allows other users to build a UserManager, especially if they are called from the UserManager ctor.
   * @return
   */
  public static UserManager getInstance() {
    return instance;
  }

  /**
   * Singleton
   * Factory Method
   * @param uiType
   * @return
   * @throws ClassNotFoundException
   * @throws IllegalAccessException
   * @throws InstantiationException
   * @throws DAOException
   */
  public static UserManager getUserManager(final int uiType) throws ClassNotFoundException, IllegalAccessException, InstantiationException, DAOException {
    if(instance == null) {
      // The instance is set in the ctor to assist builder objects called from there - the assignment here is idempotent and just for readability
      // This data source base name is the same for all DAOs of the User Manager
      String datasource = AppMgr.createDataSourceNamePrefs(kBase);
      try {
        instance = new UserManager(datasource, uiType);
      }
      catch(Exception ex) {
        AppMgr.logError(instance, Java.getStackTrace(ex, false));
        throw ex;
      }
    }
    
    return instance;
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
    final StringWriter sw = new StringWriter();
    final PrintWriter pw = new PrintWriter(sw);
    
    pw.println(LogMgr.getDumpHeader(getClass()));
    Java.printObject(pw, this);
    pw.println(Utils.lines(userListeners));
    pw.println(Utils.stringize(users, ", ", P10nMgr.annotate("No registered users")));
    pw.println(LogMgr.getDumpFooter(getClass()));
    return sw.toString();
  }

  /**
   * IxErrorClient
   * Every error entry written by this object starts with a certain prefix, after timestamp, id, and thread tags.
   * @return 
   */
  public String errorEntryPrefix() {
    return kName;
  }

  /**
   * @param maxUsers
   * @throws PropertyVetoException
   */
  public void setMaxUsers(final int maxUsers) throws PropertyVetoException {
    int oldMaxUsers = maxUsers;
    vetoableChangeSupport.fireVetoableChange("MaxUsers", oldMaxUsers, maxUsers);
    this.maxUsers = maxUsers;
  }

  /**
   * @param replaceIfExists
   * @throws PropertyVetoException
   */
  public void setReplaceIfExists(final boolean replaceIfExists) throws PropertyVetoException {
    boolean oldReplaceIfExists = replaceIfExists;
    vetoableChangeSupport.fireVetoableChange("ReplaceIfExists", oldReplaceIfExists, replaceIfExists);
    this.replaceIfExists = replaceIfExists;
  }

  /**
   * @return
   */
  public boolean isReplaceIfExists() {
    return replaceIfExists;
  }

  /**
   * Update history for user status change
   * @param user
   * @param status
   * @param reason
   */
  public void setUserStatus(final RegisteredUser user, final String status, final Object reason) {   
    if(user.getStatus() == null) {
      addHistory(new StatusChange(user.getId(), "User", status, status, "Initial State", null));
    }    
    else if(user.getStatus().equals(status) == false) {
      addHistory(new StatusChange(user.getId(), "User", user.getStatus(), status, reason, new Date()));
    }    

    user.setStatus(status);
  }

  /**
   * @param l
   */
  public void addVetoableChangeListener(VetoableChangeListener l) {
    vetoableChangeSupport.addVetoableChangeListener(l);
  }

  /**
   * @param l
   */
  public void removeVetoableChangeListener(VetoableChangeListener l) {
    vetoableChangeSupport.removeVetoableChangeListener(l);
  }
  
  /**
   * Factory Method
   * @param name
   * @return RegisteredUser
   */
  public RegisteredUser createActiveUser(final String name) {
    try {
      RegisteredUser newUser = new RegisteredUser(name);
      newUser.setUserDAO(theDAO.getName(), theDAO.getDaoType());
      newUser.setStatus(Account.kActive);
      newUser.setServer(ServerDetails.createDummyServer());
      return newUser;
    }
    catch(Exception e) {
      RegisteredUser defaultUser = getDefaultUser();
      ErrorManager.addError(this, String.format("Error (%s) creating Active User - returning default (%s)", Java.getStackTrace(e, Constants.NL_SUBSTITUTE, 4, false), defaultUser));
      return defaultUser;
    }
  }  
  
  /**
   * Factory Method
   * @param name
   * @return RegisteredUser
   */
  public RegisteredUser createActiveUser(final String name, final URL address, final String hostname) {
    try {
      RegisteredUser newUser = new RegisteredUser(name, address, hostname);
      newUser.setUserDAO(theDAO.getName(), theDAO.getDaoType());
      newUser.setStatus(Account.kActive);
      newUser.setServer(ServerDetails.createDummyServer());
      return newUser;
    }
    catch(Exception e) {
      RegisteredUser defaultUser = getDefaultUser();
      ErrorManager.addError(this, String.format("Error (%s) creating Active User - returning default (%s)", Java.getStackTrace(e, Constants.NL_SUBSTITUTE, 4, false), defaultUser));
      return defaultUser;
    }
  }  
  
  /**
   * Factory Method
   * @param name
   * @return RegisteredUser
   */
  public RegisteredUser createInactiveUser(final String name) {
    try {
      RegisteredUser newUser = new RegisteredUser(name);
      newUser.setUserDAO(theDAO.getName(), theDAO.getDaoType());
      newUser.setStatus(Account.kInactive);
      newUser.setDescription(kReconstituted);
      newUser.setServer(ServerDetails.createDummyServer());
      return newUser;
    }
    catch(Exception e) {
      RegisteredUser defaultUser = getDefaultUser();
      ErrorManager.addError(this, String.format("Error (%s) creating Inactive User - returning default (%s)", Java.getStackTrace(e, Constants.NL_SUBSTITUTE, 4, false), defaultUser));
      return defaultUser;
    }
  }  

  /**
   * Factory Method
   * @param name
   * @return RegisteredUser
   */
  public RegisteredUser createUnregisteredUser(final String name) {
    try {
      RegisteredUser newUser = new RegisteredUser(name);
      newUser.setUserDAO(theDAO.getName(), theDAO.getDaoType());
      newUser.setStatus(Account.kNotRegistered);
      newUser.setDescription(kNoUser);
      newUser.setServer(ServerDetails.createDummyServer());
      return newUser;
    }
    catch(Exception e) {
      RegisteredUser defaultUser = getDefaultUser();
      ErrorManager.addError(this, String.format("Error (%s) creating Unregistered User - returning default (%s)", Java.getStackTrace(e, Constants.NL_SUBSTITUTE, 4, false), defaultUser));
      return defaultUser;
    }
  }

  /**
   * Factory Method
   * @return RegisteredUser
   */
  public RegisteredUser createUnknownUser() {
    try {
      RegisteredUser newUser = new RegisteredUser(AppMgr.getSharedResString(Constants.kUnknownUser));
      newUser.setUserDAO(theDAO.getName(), theDAO.getDaoType());
      newUser.setStatus(Account.kInactive);
      newUser.setDescription(kNoUser);
      newUser.setServer(ServerDetails.createDummyServer());
      return newUser;
    }
    catch(Exception e) {
      RegisteredUser defaultUser = getDefaultUser();
      ErrorManager.addError(this, String.format("Error (%s) creating Unknown User - returning default (%s)", Java.getStackTrace(e, Constants.NL_SUBSTITUTE, 4, false), defaultUser));
      return defaultUser;
    }
  }

  /**
   * IxMutableManager
   * @param oldtype
   * @param newtype
   * @return
   */
  public void changeDAO(final int oldtype, final int newtype) {
    if(oldtype != newtype) {
      // Get parameters for retrieving DAO Factories for both the old and new data sources
      String host = AppMgr.getProperty(Constants.kPreference, Settings.concat("data.dao.host", kName), ConnectionFactory.kDefaultServer);
      String newdatasource = AppMgr.createDataSourceNamePrefs(kBase);
      
      // Create the new data source
      try {
        DAOFactory newFactory = DAOFactory.getDAOFactory(newtype, host, newdatasource, -1, "", "", false);
        UserManagerDAO newDAO = newFactory.getUserManagerDAO();
        newDAO.create();
        newDAO.setIncludeHistory(theDAO.isIncludeHistory());
        newDAO.populate();
        
        UserManagerDAO oldDAO = theDAO;
        theDAO = newDAO;
        
        // Archive the old data source
        try {
          oldDAO.move();
        }
        catch(final Exception e) {
          AppMgr.logTrace(e);
          ErrorManager.addError(this, String.format("Error (%s) archiving old DAO for (%s)", Java.getStackTrace(e, Constants.NL_SUBSTITUTE, 2, false), newdatasource));
        }
        
        // Ensure all users are set to the new DAO
        for(RegisteredUser aUser:users) {
          aUser.setTheDAO(newFactory.getUserDAO());
        }
      }
      catch(final Exception e) {
        AppMgr.logTrace(e);
        ErrorManager.addError(this, String.format("Error (%s) processing new DAO for (%s)", Java.getStackTrace(e, Constants.NL_SUBSTITUTE, 2, false), newdatasource));
      }
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
      IxMutableManager theCopy = (IxMutableManager)clone();
      return theCopy;
    }
    catch(final CloneNotSupportedException e) {
      ErrorManager.addError(this, Java.getStackTrace(e, Constants.NL_SUBSTITUTE, 2, false));
    }
    
    return null;
  }

  /**
   * IxMutableManager
   * Creates a copy of the source Manager with a new, temporary DAO without the expense of instantiating a new DAO object.
   * Useful to hold changes to a Manager without modifying the source and before committing any changes.
   * @param daoname
   * @return
   */
  public IxMutableManager mutate(String daoname) {
    int daotype = DAO.getType(daoname);
    if(daotype != theDAO.getDaoType()) {
      String newdatasource = AppMgr.createDataSourceNamePrefs(kBase);
      try {
        UserManager theCopy = (UserManager)clone();
        DAOFactory factory = DAOFactory.getDAOFactoryInstance(daotype, host, newdatasource, -1, "", "");
        if(factory != null) {
          theCopy.theDAO = factory.getUserManagerDAO();
          return theCopy;
        }
      }
      catch(final Exception e) {
        AppMgr.logTrace(e);
        ErrorManager.addError(this, String.format("Error %s assigning temp DAO for %s", P10nMgr.highlight(Java.getStackTrace(e, Constants.NL_SUBSTITUTE, 2, false)), P10nMgr.highlight(newdatasource)));
      }
    }

    return this;
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
    return theDAO.getDaoType();
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
    daoTypes.add(DAO.kMsAccess);
    daoTypes.add(DAO.kMySQL);
    daoTypes.add(DAO.kXML);
  }

  /**
   * @return
   */
  public UserManagerDAO getTheDAO() {
    return theDAO;
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
    if(header.equals(kComponentHeader)) {
      throw new UnsupportedOperationException("Cannot set Component header in setByHeader");
    }
    else if(header.equals(kDaoHeader)) {
      throw new UnsupportedOperationException("Cannot set DAO header in setByHeader");
    }
    else if(header.equals(kHostHeader)) {
      this.host = val.toString();
    }
    else if(header.equals(kHistoryHeader)) {
      throw new UnsupportedOperationException("Cannot set History header in setByHeader");
    }
  }

  /**
   * IxTableRow
   * @param header
   * @return 
   */
  public Object getByHeader(final String header, int mask) {
    if(header.equals(kComponentHeader)) {
      return this;
    }
    else if(header.equals(kDaoHeader)) {
      return DAO.kTypes[theDAO.getType()];
    }
    else if(header.equals(kHostHeader)) {
      return this.host;
    }
    else if(header.equals(kHistoryHeader)) {
      return theDAO.isIncludeHistory();
    }
    else {
      return null;
    }
  }
}

////////////////////////////////////////////////////////////////////////////////
//                                 REVISION HISTORY                           //
// $Log$
////////////////////////////////////////////////////////////////////////////////
