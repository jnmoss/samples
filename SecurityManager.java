package com.jmoss.kb;

import com.jmoss.AppMgr;
import com.jmoss.AxMutableManager;
import com.jmoss.Constants;
import com.jmoss.IxManager;
import com.jmoss.IxMutableManager;
import com.jmoss.data.AccessPermission;
import com.jmoss.data.ConnectionFactory;
import com.jmoss.data.DAO;
import com.jmoss.data.DAOException;
import com.jmoss.data.DAOFactory;
import com.jmoss.data.RegisteredUser;
import com.jmoss.data.SecurityManagerDAO;
import com.jmoss.data.UserRole;
import com.jmoss.data.ValidationModel;
import com.jmoss.kb.data.AdminRole;
import com.jmoss.kb.data.kbPermissionFactory;
import com.jmoss.kb.data.kbUserRoleFactory;
import com.jmoss.kb.ui.kbPAOFactory;
import com.jmoss.ui.IxSecurityManagerPAO;
import com.jmoss.ui.P10nMgr;
import com.jmoss.ui.PAO;
import com.jmoss.ui.PAOFactory;
import com.jmoss.ui.Settings;
import com.jmoss.util.Command;
import com.jmoss.util.ErrorManager;
import com.jmoss.util.HashBag;
import com.jmoss.util.IxErrorClient;
import com.jmoss.util.Java;
import com.jmoss.util.LogMgr;
import com.jmoss.util.Utils;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;
import java.beans.VetoableChangeSupport;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Vector;

/**
 * <!-- begin-UML-doc -->
 * @(#)SecurityManager.java 1/11/2010
 *
 * Copyright 1995-2020 Jeffrey Moss All Rights Reserved.
 *
 * <!-- end-UML-doc -->
 * @author jeffrey.n.moss
 * @uml.annotations
 *     derived_abstraction="platform:/resource/UML%20Project/Security%20Manager.emx#_ssQJkPiOEd6keveglHf0Tw"
 * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_ssQJkPiOEd6keveglHf0Tw"
 */
public final class SecurityManager extends AxMutableManager implements IxErrorClient, VetoableChangeListener {

  public static final String kBase = SecurityManagerDAO.kBase;

  public static final String kPermissionAll = "All";
  public static final String kPermissionCreate = "Create";

  public static final String kPropertyAvailableUsers = "Available Users";
  public static final String kPropertySelectedUsers = "Selected Users";  
  public static final String kPropertyAccessLevel = Constants.kPropAccessLevel;
  
  public static final String[] kAccessLevels = new String [] { 
    Constants.kAccessLevelUnclassifiedStr, 
    Constants.kAccessLevelConfidentialStr, 
    Constants.kAccessLevelSecretStr, 
    Constants.kAccessLevelTopSecretStr 
  };
 
  /**
   **/
  private static final SortedSet<String> permissionSet = new TreeSet<>();
  
  /**
   **/
  private static final SortedSet<String> fieldSet = new TreeSet<>();

  /**
   **/
  private HashBag userRoles = new HashBag(String.format("%s.%s", kName, "UserRoles"));

  /**
   * The Security Manager maintains access levels for KB Object keys in this map.
   * Space is minimized by omitting entries for KB Objects with default access level of Unclassified.
   * The Security Manager knows that if there is no entry for the object, then it has the default access level.
   */
  private Map<String,Integer> accessLevels = new Hashtable<>();

  private VetoableChangeSupport vetoableChangeSupport = new VetoableChangeSupport(this);

  private int maxRoles = Integer.MAX_VALUE;

  private boolean replaceIfExists = false;
  
  private int rolesPerUser = 1;
  
  private String host;

  /** 
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_YDYGMACTEd-3bfn3CXzkSA"
   */
  public static final String kPermissionDelete = "Delete";

  /** 
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_NaPkUACTEd-3bfn3CXzkSA"
   */
  public static final String kPermissionEdit = "Edit";

  /** 
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_b35gsACTEd-3bfn3CXzkSA"
   */
  public static final String kPermissionView = "View";

  /**
   * Copy permission implies Read, Write, and Paste, and therefore also implies access to clipboard
   * In this context, there would be no point in allowing a Copy without also allowing a Paste
   */
  public static final String kPermissionCopyPaste = "Copy/Paste";

  /**
   * Paste permission implies Copy, which implies, Read, Write, and Paste, and therefore also implies access to clipboard
   * In this context, there would be no point in allowing a Cut without also allowing a Paste
   */
  public static final String kPermissionCutPaste = "Cut/Copy/Paste";

  static {
    permissionSet.add(kPermissionCreate);
    permissionSet.add(kPermissionDelete);
    permissionSet.add(kPermissionEdit);
    permissionSet.add(kPermissionView);
  }
  
  /** 
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_mwozkACNEd-3bfn3CXzkSA"
   */
  private static SecurityManager instance;

  /** 
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_dbGZ0ACIEd-3bfn3CXzkSA"
   */
  private static final int kDefaultDAO = DAO.kTypeXML;

  /** 
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_nRmIYACJEd-3bfn3CXzkSA"
   */
  private static final int kDefaultPAO = PAO.kTypeSwing;

  /** 
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_AV-2EACOEd-3bfn3CXzkSA"
   */
  private static final String kName = "KB Security Manager";

  /** 
   * @return the instance
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_mwozkACNEd-3bfn3CXzkSA?GETTER"
   */
  public static SecurityManager getInstance() {
    // begin-user-code
    return instance;
    // end-user-code
  }

  /**
   **/
  public static final String kMemberSetFieldAll = "All";

  public static final String kMemberSetFieldContext = "context";

  /** 
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_ULDZMARWEd-3bfn3CXzkSA"
   */
  public static final String kMemberSetFieldAlias = "alias";

  /** 
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_uTaCIARWEd-3bfn3CXzkSA"
   */
  public static final String kMemberSetFieldHistory = "history";

  /** 
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_y78TgARWEd-3bfn3CXzkSA"
   */
  public static final String kMemberSetFieldIcon = "icon";

  /** 
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_3uwiQARWEd-3bfn3CXzkSA"
   */
  public static final String kMemberSetFieldLocale = "locale";

  /** 
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_8L9tYARWEd-3bfn3CXzkSA"
   */
  public static final String kMemberSetFieldOwnership = "ownership";

  /** 
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_AIGLwARXEd-3bfn3CXzkSA"
   */
  public static final String kMemberSetFieldPhonics = "phonics";

  /**
   **/
  public static final String kMemberSetFieldPlural = "plural";

  /** 
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_EtzLkARXEd-3bfn3CXzkSA"
   */
  public static final String kMemberSetFieldProper = "proper";

  /** 
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_H6PSYARXEd-3bfn3CXzkSA"
   */
  public static final String kMemberSetFieldReferent = "referent";

  /** 
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_MDLdsARXEd-3bfn3CXzkSA"
   */
  public static final String kMemberSetFieldSymbol = "symbol";

  /** 
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_PdoyYARXEd-3bfn3CXzkSA"
   */
  public static final String kMemberSetFieldType = "type";

  static {
    fieldSet.add(kMemberSetFieldContext);
    fieldSet.add(kMemberSetFieldAlias);
    fieldSet.add(kMemberSetFieldPlural);
    fieldSet.add(kMemberSetFieldHistory);
    fieldSet.add(kMemberSetFieldIcon);
    fieldSet.add(kMemberSetFieldLocale);
    fieldSet.add(kMemberSetFieldOwnership);
    fieldSet.add(kMemberSetFieldPhonics);
    fieldSet.add(kMemberSetFieldProper);
    fieldSet.add(kMemberSetFieldReferent);
    fieldSet.add(kMemberSetFieldSymbol);
    fieldSet.add(kMemberSetFieldType);
  }
  
  /** 
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_4X__APiOEd6keveglHf0Tw"
   */
  private boolean contextOverrides;

  /** 
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_sog20PlDEd6keveglHf0Tw"
   */
  private SortedSet<UserRole> roles = new TreeSet<>();

  /** 
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_cRt0EACBEd-3bfn3CXzkSA"
   */
  private SecurityManagerDAO theDAO;

  /** 
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_exKSAACBEd-3bfn3CXzkSA"
   */
  private IxSecurityManagerPAO thePAO;

  /** 
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_lwhbYACCEd-3bfn3CXzkSA"
   */
  public SecurityManager() throws ClassNotFoundException, IllegalAccessException, InstantiationException, DAOException {
    // begin-user-code
    LogMgr.putTrace(getClass(), "SecurityManager");

    int daotype = Integer.valueOf(AppMgr.getProperty(Constants.kPreference, Settings.concat("data.dao.type", kName), String.valueOf(kDefaultDAO))).intValue();
    host = AppMgr.getProperty(Constants.kPreference, Settings.concat("com.jmoss.kb.data.dao.host", kName), ConnectionFactory.kDefaultServer);

    String datasource = AppMgr.createDataSourceNamePrefs(kBase);
    AppMgr.logEvent(this, String.format("Retrieving roles from data source %s/%s", host, datasource));

    // Set the instance as early as possible to allow other objects (like a DAO) to help build the SecurityManager, being that they are called from the ctor
    instance = this;

    DAOFactory daoFactory = DAOFactory.getDAOFactory(daotype, host, datasource, -1, "", "", false);
    if(daoFactory != null) {
      // Register this component as a user of the configured DAO type
      kbMgr.registerDaoClient(kName, daotype);

      // Register the factory for User Roles
      AppMgr.registerDaoFactory(kName, daoFactory);

      // Set the DAO
      theDAO = daoFactory.getSecurityManagerDAO(Constants.kLoadAction, new kbUserRoleFactory(), new kbPermissionFactory());

      if(roles.isEmpty()) {
        if(theDAO.getRoles() != null) {
          if(theDAO.getRoles().size() >= maxRoles) {
            throw new IllegalStateException(String.format("Attempted to load (%d) roles while the maximum is set at (%d)", theDAO.getRoles().size(), maxRoles));
          }

          roles.addAll(theDAO.getRoles());
        }
      }
    
      if(userRoles.isEmpty() && theDAO.getUserRoles() != null) {
        userRoles.putAll(theDAO.getUserRoles());
      }                  
      
      if(accessLevels.isEmpty() && theDAO.getAccessLevels() != null) {
        accessLevels.putAll(theDAO.getAccessLevels());
      }
    }
    else {
      throw new IllegalArgumentException(DAO.kUnknown);
    }

    int paotype = Integer.valueOf(AppMgr.getProperty(Constants.kPreference, Settings.concat("com.jmoss.kb.data.pao.type", kName), String.valueOf(kDefaultPAO))).intValue();
    PAOFactory paoFactory = kbPAOFactory.getPAOFactory(paotype);
    if(paoFactory != null) {
      thePAO = paoFactory.getSecurityManagerPAO(kName);
    }
    else {
      throw new IllegalArgumentException(PAO.kUnknown);
    }
    
    populateSupportedTypes();

    LogMgr.popTrace("SecurityManager");
    // end-user-code
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
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @param role
   * @param doSave
   * @return
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_jVvG4AB1Ed-3bfn3CXzkSA"
   */
  public boolean addRole(final UserRole role, final boolean doSave) {
    // begin-user-code
    if(roles.size() == maxRoles) {
      throw new IllegalStateException(String.format("Did not add user role (%s) because the maximum number of roles has been reached (%d)", role, maxRoles));
    }

    if(replaceIfExists == false && roles.contains(role)) {
      AppMgr.logEvent(this, String.format("Did not add user role (%s) because it already exists and Replace-if-Exists=false", role));
      return false;
    }

    boolean added = roles.add(role);

    if(doSave) {
      // Write the changes out
      role.save();
      
      theDAO.setRoles(roles);
      theDAO.setAccessLevels(accessLevels);
      theDAO.setUserRoles(userRoles);
      theDAO.save();
    }

    return added;
    // end-user-code
  }

  /**
   * @param role
   * @param user
   * @param doSave
   * @return
   */
  public boolean addUserToRole(final UserRole role, final RegisteredUser user, final boolean doSave) {
    // Determine if the current Security model allows users to be assigned to multiple roles
    int i = userRoles.occurencesOf(user);
    if(i >= rolesPerUser) {
      throw new IllegalStateException(String.format("Did not add user role (%s) because the maximum number of roles per user (%d) has been reached for (%s)", role, rolesPerUser, user));
    }
    
    boolean added = userRoles.put(role, user) != null;    

    if(doSave) {
      // Write the changes out
      theDAO.setRoles(roles);
      theDAO.setUserRoles(userRoles);
      theDAO.setAccessLevels(accessLevels);
      theDAO.save();
    }

    return added;
  }

  /**
   * Accessor
   * @param name
   * @param typename
   * @return
   */
  public UserRole getUserRoleById(final String name, final String typename) {
    for(UserRole role:roles) {
      if(role.getName().equals(name) && role.getTypeName().equals(typename)) {
        return role;
      }
    }

    return null;
  }

  /**
   * @param theCategory
   * @return
   * @throws Exception
   */
  public boolean addCategory(final kbCategory theCategory) throws Exception {
    // Get a DAO that encapsulates an archive for theCategory
    // SecurityManagerDAO dao = theDAO.loadContext(theCategory.key());
    SecurityManagerDAO dao = null;

    if(dao != null) {
      Map<String, Integer> levels = dao.getAccessLevels();
      if(levels != null) {
        accessLevels.putAll(levels);
      }

      SortedSet<UserRole> set = dao.getRoles();
      if(set != null) {
        String host = AppMgr.getProperty(Constants.kPreference, Settings.concat("data.dao.host", kName), ConnectionFactory.kDefaultServer);
        DAOFactory factory = DAOFactory.getDAOFactory(theDAO.getDaoType(), host, theDAO.getName(), -1, "", "", false);
        for(UserRole aRole : set) {
          // This can result in multiple writes if their are multiple roles
          aRole.setTheDAO(factory.getUserRoleDAO());
          aRole.getTheDAO().setUserRole(aRole);
          addRole(aRole, true);
        }

        HashBag bag = dao.getUserRoles();
        if(bag != null) {
          userRoles.putAll(bag);
        }

        // Write the changes out
        theDAO.setRoles(roles);
        theDAO.setUserRoles(userRoles);
        theDAO.setAccessLevels(accessLevels);
        theDAO.save();

        dao.drop();

        return levels != null || set.size() > 0 || bag != null;
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
   * @param theContext
   * @return
   * @throws Exception
   */
  public boolean addContext(final kbContext theContext) throws Exception {
    // Get a DAO that encapsulates an archive for theContext
    SecurityManagerDAO dao = theDAO.loadContext(theContext.key());

    if(dao != null) {
      Map<String, Integer> levels = dao.getAccessLevels();
      if(levels != null) {
        accessLevels.putAll(levels);
      }

      SortedSet<UserRole> set = dao.getRoles();
      if(set != null) {
        String host = AppMgr.getProperty(Constants.kPreference, Settings.concat("data.dao.host", kName), ConnectionFactory.kDefaultServer);
        DAOFactory factory = DAOFactory.getDAOFactory(theDAO.getDaoType(), host, theDAO.getName(), -1, "", "", false);
        for(UserRole aRole : set) {
          // This can result in multiple writes if their are multiple roles
          aRole.setTheDAO(factory.getUserRoleDAO());
          aRole.getTheDAO().setUserRole(aRole);
          addRole(aRole, true);
        }

        HashBag bag = dao.getUserRoles();
        if(bag != null) {
          userRoles.putAll(bag);
        }

        // Write the changes out
        theDAO.setRoles(roles);
        theDAO.setUserRoles(userRoles);
        theDAO.setAccessLevels(accessLevels);
        theDAO.save();

        dao.drop();

        return levels != null || set.size() > 0 || bag != null;
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
   * @param theContext
   * @param objects
   * @param daotype
   * @return
   * @throws Exception
   */
  public boolean removeContext(final kbContext theContext, final List<kbObject> objects, final int daotype) throws Exception {
    SecurityManager mgr = (SecurityManager)copy();
    
    // Use the specified DAO type for the archive data 
    mgr.changeDAO(theDAO.getDaoType(), daotype);
    
    mgr.accessLevels.clear();
    mgr.roles.clear();
    mgr.userRoles.clear();
    
    for(kbObject anObject : objects) {
      Integer val = accessLevels.get(anObject.getObj());
      if(val != null) {
        accessLevels.remove(anObject.getObj());
        mgr.accessLevels.put(anObject.getObj(), val);
      }
    }
    
    for(UserRole role:roles) {
      UserRole archiveRole = role.copy();
      archiveRole.clearPermissions();
      
      Collection<AccessPermission> perms = role.getPermissions();
      for(AccessPermission p : perms) {
        boolean b = p.getContext().equals(theContext.getSymbol());
        if(b) {
          role.removePermission(p);          
          archiveRole.addPermission(p);
        }
      }

      // Do not remove role from roles here - ConcurrentModificationException

      if(Utils.isNullOrEmpty(archiveRole.getPermissions()) == false) {
        mgr.addRole(archiveRole, false);
      }
    }
    
    if(mgr.accessLevels.size() > 0 || mgr.roles.size() > 0) {      
      mgr.theDAO.setRoles(mgr.roles);
      mgr.theDAO.setAccessLevels(mgr.accessLevels);
      mgr.theDAO.setUserRoles(mgr.userRoles);

      // Set the DAO to an archive location
      mgr.theDAO.setArchiveKey(theContext.key());
      mgr.theDAO.setMode(Constants.kReplaceAction);
      mgr.theDAO.replicate();
      
      // If any roles were added to the archive manager, remove them from this manager
      for(UserRole role : mgr.roles) {
        // Get the equivalent role for this Manager / DAO ...
        UserRole thisrole = Utils.get(roles, role);
        
        // ... and remove it
        removeRole(thisrole);
      }
      
      return true;
    }
    else {
      return false;      
    }
  }

  /**
   * @param theContext
   * @param objects
   * @return
   * @throws Exception
   */
  public boolean purgeContext(final kbContext theContext, final List<kbObject> objects) throws Exception {
    for(kbObject anObject : objects) {
      Integer val = accessLevels.get(anObject.getObj());
      if(val != null) {
        accessLevels.remove(anObject.getObj());
      }
    }
    
    int removed = 0;
    List<UserRole> list = new ArrayList<>(roles);
    for(int i = list.size()-1; i >= 0; i--) {
      UserRole role = list.get(i);
      Collection<AccessPermission> perms = role.getPermissions();
      for(AccessPermission p : perms) {
        boolean b = p.getContext().equals(theContext.getSymbol());
        if(b) {
          removeRole(role);
          removed++;
        }
      }
    }
    
    return removed > 0;
  }

  /**
   * @param role
   * @return
   */
  public List<RegisteredUser> getUsersByRole(final UserRole role) {
    return userRoles.getAll(role);
  }
  
  /**
   * @param role
   * @param users
   * @return
   */
  public void setUsersByRole(final UserRole role, final List<RegisteredUser> users) {
    userRoles.remove(role);
    for(RegisteredUser user:users) {
      userRoles.put(role, user);
    }
  }

  /**
   * @param user
   * @return
   */
  public List<UserRole> getRolesForUser(final RegisteredUser user) {
    List<UserRole> out = new ArrayList<>();
    
    for(Object o : userRoles.keySet()) {
      UserRole role = (UserRole)o;
      List users = userRoles.getAll(role);
      if(users.contains(user)) {
        out.add(role);
      }
    }
    
    return out;
  }

  /**
   * @param theContext
   * @return
   */
  public List<UserRole> getRolesForContext(final kbContext theContext) {
    List<UserRole> out = new ArrayList<>();
    for(UserRole role : roles) {
      Collection<AccessPermission> perms = role.getPermissions();
      for(AccessPermission p : perms) {
        if(p.getContext().equals(theContext.getSymbol())) {
          out.add(role);
        }
      }
    }

    return out;
  }

  /**
   * @param user
   * @return
   */
  public boolean isAdmin(final RegisteredUser user) {
    for(Object o : userRoles.keySet()) {
      UserRole role = (UserRole)o;
      List users = userRoles.getAll(role);
      if(users.contains(user)) {
        if(role instanceof AdminRole) {
          return true;
        }
      }
    }

    return false;
  }
  
  /**
   * @param actions
   * @param theAction
   * @return
   */
  private boolean checkActions(final String actions, final String theAction) {
    if(actions.equals(kPermissionAll)) {
      return true;
    }
    else {
      return actions.indexOf(theAction) >= 0;
    }
  }

  /**
   * @param categories
   * @param theCategory
   * @return
   */
  private boolean checkCategories(final List<kbCategory> categories, final String theCategory) {
    if(theCategory.equals("All")) {
      return true;
    }
    else {
      kbCategory c = kbMgr.getCategoryManager().fetchCategoryBySymbol(theCategory);
      for(kbCategory context : categories) {
        if(context.equals(c) || c.isAncestor(context)) {
          return true;
        }
      }
      
      return false;
    }
  }

  /**
   * @param contexts
   * @param theContext
   * @return
   */
  private boolean checkContexts(final List<kbContext> contexts, final String theContext) {
    if(theContext.equals("All")) {
      return true;
    }
    else {
      kbContext c = kbMgr.getContextManager().fetchContextBySymbol(theContext);
      for(kbContext context : contexts) {
        if(context.equals(c) || c.isAncestor(context)) {
          return true;
        }
      }
      
      return false;
    }
  }
  
  /** 
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @param code
   * @return
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_tYycIP70Ed63bfn3CXzkSA"
   */
  public boolean checkFeatureCode(final String code) {
    // begin-user-code
    // TODO Auto-generated method stub
    return true;
    // end-user-code
  }

  /**
   * @param theContext
   * @param theField
   * @return
   */
  private boolean checkField(final String theContext, final String theField) {
    if(theContext.equals("All")) {
      return true;
    }
    else {
      return theContext.equals(theField);
    }
  }

  /** 
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @param password
   * @return
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_VRed4P70Ed63bfn3CXzkSA"
   */
  public boolean checkPasswordComplexity(final String password) {
    // begin-user-code
    // TODO Specify complexity rules: min, max, charset, similarity, etc.
    ValidationModel validator = ValidationModel.getPasswordValidator(kName, 0, 32, false);
    return validator.validate(password);
    // end-user-code
  }

  /**
   * @param user
   * @param category
   * @return
   */
  public boolean deleteCategory(final RegisteredUser user, final kbCategory category) {
    AccessPermission p = getPermissions(user, category, kMemberSetFieldContext);
    if(p != null) {
      boolean allowed = checkActions(p.getActions(), kPermissionDelete);
      if(allowed) {
        allowed = userMayAccess(category.key(), user);
        return allowed;
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
   * @param context
   * @return
   */
  public boolean deleteContext(final RegisteredUser user, final kbContext context) {
    AccessPermission p = getPermissions(user, context, kMemberSetFieldContext);
    if(p != null) {
      boolean allowed = checkActions(p.getActions(), kPermissionDelete);
      if(allowed) {
        allowed = userMayAccess(context.key(), user);
        return allowed;
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
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @param user
   * @param object
   * @return
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_AtmqwP-wEd63bfn3CXzkSA"
   */
  public boolean deleteAlias(final RegisteredUser user, final kbObject object) {
    // begin-user-code
    AccessPermission p = getPermissions(user, object, kMemberSetFieldAlias);
    if(p != null) {
      boolean allowed = checkActions(p.getActions(), kPermissionDelete);
      if(allowed) {
        allowed = userMayAccess(object.getObj(), user);
        return allowed;
      }
      else {
        return false;
      }
    }
    else {
      return false;
    }
    // end-user-code
  }

  /** 
   * @param user
   * @param object
   * @return
   */
  public boolean deletePlural(RegisteredUser user, kbObject object) {
    AccessPermission p = getPermissions(user, object, kMemberSetFieldPlural);
    if(p != null) {
      boolean allowed = checkActions(p.getActions(), kPermissionDelete);
      if(allowed) {
        allowed = userMayAccess(object.getObj(), user);
        return allowed;
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
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @param user
   * @param object
   * @return
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_Ea-7cP-wEd63bfn3CXzkSA"
   */
  public boolean deleteHistory(RegisteredUser user, kbObject object) {
    // begin-user-code
    AccessPermission p = getPermissions(user, object, kMemberSetFieldHistory);
    if(p != null) {
      boolean allowed = checkActions(p.getActions(), kPermissionDelete);
      if(allowed) {
        allowed = userMayAccess(object.getObj(), user);
        return allowed;
      }
      else {
        return false;
      }
    }
    else {
      return false;
    }
    // end-user-code
  }

  /** 
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @param user
   * @param object
   * @return
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_HK9vwP-wEd63bfn3CXzkSA"
   */
  public boolean deleteIcon(RegisteredUser user, kbObject object) {
    // begin-user-code
    AccessPermission p = getPermissions(user, object, kMemberSetFieldIcon);
    if(p != null) {
      boolean allowed = checkActions(p.getActions(), kPermissionDelete);
      if(allowed) {
        allowed = userMayAccess(object.getObj(), user);
        return allowed;
      }
      else {
        return false;
      }
    }
    else {
      return false;
    }
    // end-user-code
  }

  /** 
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @param user
   * @param object
   * @return
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_KLx1YP-wEd63bfn3CXzkSA"
   */
  public boolean deleteOwnership(RegisteredUser user, kbObject object) {
    // begin-user-code
    AccessPermission p = getPermissions(user, object, kMemberSetFieldOwnership);
    if(p != null) {
      boolean allowed = checkActions(p.getActions(), kPermissionDelete);
      if(allowed) {
        allowed = userMayAccess(object.getObj(), user);
        return allowed;
      }
      else {
        return false;
      }
    }
    else {
      return false;
    }
    // end-user-code
  }

  /** 
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @param user
   * @param object
   * @return
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_NvB5sP-wEd63bfn3CXzkSA"
   */
  public boolean deletePhonics(RegisteredUser user, kbObject object) {
    // begin-user-code
    AccessPermission p = getPermissions(user, object, kMemberSetFieldPhonics);
    if(p != null) {
      boolean allowed = checkActions(p.getActions(), kPermissionDelete);
      if(allowed) {
        allowed = userMayAccess(object.getObj(), user);
        return allowed;
      }
      else {
        return false;
      }
    }
    else {
      return false;
    }
    // end-user-code
  }

  /**
   * (non-Javadoc)
   * @see IxLogClient#dump(int)
   * @generated "sourceid:mmi:///#jmethod^kBase=dump^sign=(I)QString;[jsrctype^kBase=IxLogClient[jcu^kBase=IxLogClient.java[jpack^kBase=com.jmoss.util[jsrcroot^srcfolder=src[project^id=UML-to-Java Project]]]]]$uml.Operation?INHERITED"
   */
  public String dump(final int verbose) {
    // begin-user-code
    return kName;
    // end-user-code
  }

  /**
   * Fulfill role of the Model
   * Invoke a controller object to manage user input
   * @param cmd
   * @return
   */
  public boolean create(final Command cmd) {
    return thePAO.create(cmd);
  }

  /**
   * Fulfill role of the Model
   * Invoke a controller object to manage user input
   * @param role
   * @param cmd
   */
  public void edit(final UserRole role, final Command cmd) {
    thePAO.edit(role, cmd);
  }

  /**
   * Fulfill role of the Model
   * Invoke a controller object to manage user input
   * @param user
   * @param cmd
   */
  public void edit(final RegisteredUser user, final Command cmd) {
    thePAO.edit(user, cmd);
  }

  /**
   * @param clientId
   */
  public void showUsers(final String clientId) {
    thePAO.showUsers(clientId);
  }

  /**
   * @param clientId
   */
  public void showUserRoles(final String clientId) {
    thePAO.showUserRoles(clientId);
  }

  /**
   * @param user
   * @param category
   * @return
   */
  public boolean editCategory(final RegisteredUser user, final kbCategory category) {
    AccessPermission p = getPermissions(user, category, kMemberSetFieldContext);
    if(p != null) {
      boolean allowed = checkActions(p.getActions(), kPermissionEdit);
      if(allowed) {
        allowed = userMayAccess(category.key(), user);
        return allowed;
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
   * @param context
   * @return
   */
  public boolean editContext(final RegisteredUser user, final kbContext context) {
    AccessPermission p = getPermissions(user, context, kMemberSetFieldContext);
    if(p != null) {
      boolean allowed = checkActions(p.getActions(), kPermissionEdit);
      if(allowed) {
        allowed = userMayAccess(context.key(), user);
        return allowed;
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
   * @param object
   * @return
   */
  public boolean createAlias(final RegisteredUser user, final kbObject object) {
    AccessPermission p = getPermissions(user, object, kMemberSetFieldAlias);
    if(p != null) {
      boolean allowed = checkActions(p.getActions(), kPermissionCreate);
      if(allowed) {
        allowed = userMayAccess(object.getObj(), user);
        return allowed;
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
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @param user
   * @param object
   * @return
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_kV-QcP73Ed63bfn3CXzkSA"
   */
  public boolean editAlias(final RegisteredUser user, final kbObject object) {
    // begin-user-code
    AccessPermission p = getPermissions(user, object, kMemberSetFieldAlias);
    if(p != null) {
      boolean allowed = checkActions(p.getActions(), kPermissionEdit);
      if(allowed) {
        allowed = userMayAccess(object.getObj(), user);
        return allowed;
      }
      else {
        return false;
      }
    }
    else {
      return false;
    }
    // end-user-code
  }

  /** 
   * @param user
   * @param object
   * @return
   */
  public boolean createPlural(RegisteredUser user, kbObject object) {
    AccessPermission p = getPermissions(user, object, kMemberSetFieldPlural);
    if(p != null) {
      boolean allowed = checkActions(p.getActions(), kPermissionCreate);
      if(allowed) {
        allowed = userMayAccess(object.getObj(), user);
        return allowed;
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
   * @param object
   * @return
   */
  public boolean editPlural(final RegisteredUser user, final kbObject object) {
    AccessPermission p = getPermissions(user, object, kMemberSetFieldPlural);
    if(p != null) {
      boolean allowed = checkActions(p.getActions(), kPermissionEdit);
      if(allowed) {
        allowed = userMayAccess(object.getObj(), user);
        return allowed;
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
   * @param object
   * @return
   */
  public boolean createHistory(final RegisteredUser user, final kbObject object) {
    AccessPermission p = getPermissions(user, object, kMemberSetFieldHistory);
    if(p != null) {
      boolean allowed = checkActions(p.getActions(), kPermissionCreate);
      if(allowed) {
        allowed = userMayAccess(object.getObj(), user);
        return allowed;
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
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @param user
   * @param object
   * @return
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_wukLg_78Ed63bfn3CXzkSA"
   */
  public boolean editHistory(final RegisteredUser user, final kbObject object) {
    // begin-user-code
    AccessPermission p = getPermissions(user, object, kMemberSetFieldHistory);
    if(p != null) {
      boolean allowed = checkActions(p.getActions(), kPermissionEdit);
      if(allowed) {
        allowed = userMayAccess(object.getObj(), user);
        return allowed;
      }
      else {
        return false;
      }
    }
    else {
      return false;
    }
    // end-user-code
  }

  /** 
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @param user
   * @param object
   * @return
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_Y2m34_78Ed63bfn3CXzkSA"
   */
  public boolean editIcon(final RegisteredUser user, final kbObject object) {
    // begin-user-code
    AccessPermission p = getPermissions(user, object, kMemberSetFieldIcon);
    if(p != null) {
      boolean allowed = checkActions(p.getActions(), kPermissionEdit);
      if(allowed) {
        allowed = userMayAccess(object.getObj(), user);
        return allowed;
      }
      else {
        return false;
      }
    }
    else {
      return false;
    }
    // end-user-code
  }

  /** 
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @param user
   * @param object
   * @return
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_Cx-jEP78Ed63bfn3CXzkSA"
   */
  public boolean editLocale(final RegisteredUser user, final kbObject object) {
    // begin-user-code
    AccessPermission p = getPermissions(user, object, kMemberSetFieldLocale);
    if(p != null) {
      boolean allowed = checkActions(p.getActions(), kPermissionEdit);
      if(allowed) {
        allowed = userMayAccess(object.getObj(), user);
        return allowed;
      }
      else {
        return false;
      }
    }
    else {
      return false;
    }
    // end-user-code
  }

  /**
   * @param user
   * @param object
   * @return
   */
  public boolean createOwnership(final RegisteredUser user, final kbObject object) {
    AccessPermission p = getPermissions(user, object, kMemberSetFieldOwnership);
    if(p != null) {
      boolean allowed = checkActions(p.getActions(), kPermissionCreate);
      if(allowed) {
        allowed = userMayAccess(object.getObj(), user);
        return allowed;
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
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @param user
   * @param object
   * @return
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_1AJ8g_78Ed63bfn3CXzkSA"
   */
  public boolean editOwnership(final RegisteredUser user, final kbObject object) {
    // begin-user-code
    AccessPermission p = getPermissions(user, object, kMemberSetFieldOwnership);
    if(p != null) {
      boolean allowed = checkActions(p.getActions(), kPermissionEdit);
      if(allowed) {
        allowed = userMayAccess(object.getObj(), user);
        return allowed;
      }
      else {
        return false;
      }
    }
    else {
      return false;
    }
    // end-user-code
  }

  /**
   * @param user
   * @param object
   * @return
   */
  public boolean createPhonics(final RegisteredUser user, final kbObject object) {
    AccessPermission p = getPermissions(user, object, kMemberSetFieldPhonics);
    if(p != null) {
      boolean allowed = checkActions(p.getActions(), kPermissionCreate);
      if(allowed) {
        allowed = userMayAccess(object.getObj(), user);
        return allowed;
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
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @param user
   * @param object
   * @return
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_rrqSU_78Ed63bfn3CXzkSA"
   */
  public boolean editPhonics(final RegisteredUser user, final kbObject object) {
    // begin-user-code
    AccessPermission p = getPermissions(user, object, kMemberSetFieldPhonics);
    if(p != null) {
      boolean allowed = checkActions(p.getActions(), kPermissionEdit);
      if(allowed) {
        allowed = userMayAccess(object.getObj(), user);
        return allowed;
      }
      else {
        return false;
      }
    }
    else {
      return false;
    }
    // end-user-code
  }

  /** 
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @param user
   * @param object
   * @return
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_jLUo8_78Ed63bfn3CXzkSA"
   */
  public boolean editProper(RegisteredUser user, kbObject object) {
    // begin-user-code
    AccessPermission p = getPermissions(user, object, kMemberSetFieldProper);
    if(p != null) {
      boolean allowed = checkActions(p.getActions(), kPermissionEdit);
      if(allowed) {
        allowed = userMayAccess(object.getObj(), user);
        return allowed;
      }
      else {
        return false;
      }
    }
    else {
      return false;
    }
    // end-user-code
  }

  /** 
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @param user
   * @param object
   * @return
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_nQGF8_78Ed63bfn3CXzkSA"
   */
  public boolean editReferent(RegisteredUser user, kbObject object) {
    // begin-user-code
    AccessPermission p = getPermissions(user, object, kMemberSetFieldReferent);
    if(p != null) {
      boolean allowed = checkActions(p.getActions(), kPermissionEdit);
      if(allowed) {
        allowed = userMayAccess(object.getObj(), user);
        return allowed;
      }
      else {
        return false;
      }
    }
    else {
      return false;
    }
    // end-user-code
  }

  /**
   * @param user
   * @param object
   * @return
   */
  public boolean createSymbol(final RegisteredUser user, final kbObject object) {
    AccessPermission p = getPermissions(user, object, kMemberSetFieldSymbol);
    if(p != null) {
      boolean allowed = checkActions(p.getActions(), kPermissionCreate);
      if(allowed) {
        allowed = userMayAccess(object.getObj(), user);
        return allowed;
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
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @param user
   * @param object
   * @return
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_oNLEQPiQEd6keveglHf0Tw"
   */
  public boolean editSymbol(final RegisteredUser user, final kbObject object) {
    // begin-user-code
    AccessPermission p = getPermissions(user, object, kMemberSetFieldSymbol);
    if(p != null) {
      boolean allowed = checkActions(p.getActions(), kPermissionEdit);
      if(allowed) {
        allowed = userMayAccess(object.getObj(), user);
        return allowed;
      }
      else {
        return false;
      }
    }
    else {
      return false;
    }
    // end-user-code
  }

  /** 
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @param user
   * @param object
   * @return
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_fqUAU_78Ed63bfn3CXzkSA"
   */
  public boolean editType(RegisteredUser user, kbObject object) {
    // begin-user-code
    AccessPermission p = getPermissions(user, object, kMemberSetFieldType);
    if(p != null) {
      boolean allowed = checkActions(p.getActions(), kPermissionEdit);
      if(allowed) {
        allowed = userMayAccess(object.getObj(), user);
        return allowed;
      }
      else {
        return false;
      }
    }
    else {
      return false;
    }
    // end-user-code
  }

  /**
   * IxErrorClient
   * Every error entry written by this object starts with a certain prefix
   * after timestamp, id, and thread tags.
   * @return
   */
  public String errorEntryPrefix() {
    return kName;
  }

  /**
   * @param maxRoles
   * @throws PropertyVetoException
   */
  public void setMaxRoles(final int maxRoles) throws PropertyVetoException {
    int oldMaxRoles = maxRoles;
    vetoableChangeSupport.fireVetoableChange("Maxroles", oldMaxRoles, maxRoles);
    this.maxRoles = maxRoles;
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
   * @param rolesPerUser
   * @throws PropertyVetoException
   */
  public void setRolesPerUser(final int rolesPerUser) throws PropertyVetoException {
    int oldRolesPerUser = rolesPerUser;
    vetoableChangeSupport.fireVetoableChange("RolesPerUser", oldRolesPerUser, rolesPerUser);
    this.rolesPerUser = rolesPerUser;
  }

  /**
   * @return
   */
  public int getRolesPerUser() {
    return rolesPerUser;
  }

  /** 
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @param user
   * @return
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_-HdcMPiOEd6keveglHf0Tw"
   */
  public boolean findInContexts(final RegisteredUser user) {
    // begin-user-code
    // TODO Auto-generated method stub
    return false;
    // end-user-code
  }

  /** 
   * @return the contextOverrides
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_4X__APiOEd6keveglHf0Tw?GETTER"
   */
  public boolean getContextOverrides() {
    // begin-user-code
    return contextOverrides;
    // end-user-code
  }

  /**
   * Does the user have permissions to do anything with the context(s) of the Object
   * @param user
   * @param theObject
   * @return
   */
  public AccessPermission getPermissions(final RegisteredUser user, final kbObject theObject) {
    UserRole theRole = (UserRole)userRoles.getKeyForValue(user);
    try {
      Set<AccessPermission> permissions = theRole.getPermissions();
      for(AccessPermission p : permissions) {
        if(checkContexts(theObject.getContexts(), p.getContext())) {
          return p;
        }
      }
    }
    catch(final NullPointerException e) {
      ErrorManager.addError(this, String.format("No roles for user %s", P10nMgr.highlight(user.toString())));
    }
    
    return null;
  }

  /**
   * @param user
   * @param contexts
   * @return
   */
  public AccessPermission getPermissions(final RegisteredUser user, final List<kbContext> contexts) {
    UserRole theRole = (UserRole)userRoles.getKeyForValue(user);
    try {
      Set<AccessPermission> permissions = theRole.getPermissions();
      for(AccessPermission p : permissions) {
        if(checkContexts(contexts, p.getContext())) {
          return p;
        }
      }
    }
    catch(final NullPointerException e) {
      ErrorManager.addError(this, String.format("No roles for user %s", P10nMgr.highlight(user.toString())));
    }
    
    return null;
  }

  /**
   * @param user
   * @param theCategory
   * @param theField
   * @return
   */
  public AccessPermission getPermissions(final RegisteredUser user, final kbCategory theCategory, final String theField) {
    UserRole theRole = (UserRole)userRoles.getKeyForValue(user);
    try {
      Set<AccessPermission> permissions = theRole.getPermissions();
      for(AccessPermission p : permissions) {
        if(checkField(p.getObject(), theField)) {
          if(checkCategories(Collections.singletonList(theCategory), p.getContext())) {
            return p;
          }
        }
      }
    }
    catch(final NullPointerException e) {
      ErrorManager.addError(this, String.format("No roles for user %s", P10nMgr.highlight(user.toString())));
    }
    
    return null;
  }

  /**
   * @param user
   * @param theContext
   * @param theField
   * @return
   */
  public AccessPermission getPermissions(final RegisteredUser user, final kbContext theContext, final String theField) {
    UserRole theRole = (UserRole)userRoles.getKeyForValue(user);
    try {
      Set<AccessPermission> permissions = theRole.getPermissions();
      for(AccessPermission p : permissions) {
        if(checkField(p.getObject(), theField)) {
          if(checkContexts(Collections.singletonList(theContext), p.getContext())) {
            return p;
          }
        }
      }
    }
    catch(final NullPointerException e) {
      ErrorManager.addError(this, String.format("No roles for user %s", P10nMgr.highlight(user.toString())));
    }
    
    return null;
  }
 
  /**
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @param user
   * @param theObject
   * @param theField
   * @return
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_qtGbMABjEd-3bfn3CXzkSA"
   */
  public AccessPermission getPermissions(final RegisteredUser user, final kbObject theObject, final String theField) {
    // begin-user-code
    UserRole theRole = (UserRole)userRoles.getKeyForValue(user);
    try {
      Set<AccessPermission> permissions = theRole.getPermissions();
      for(AccessPermission p : permissions) {
        if(checkField(p.getObject(), theField)) {
          if(checkContexts(theObject.getContexts(), p.getContext())) {
            return p;
          }
        }
      }
    }
    catch(final NullPointerException e) {
      ErrorManager.addError(this, String.format("No roles for user %s", P10nMgr.highlight(user.toString())));
    }
    
    return null;
    // end-user-code
  }

  /** 
   * @return the roles
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_sog20PlDEd6keveglHf0Tw?GETTER"
   */
  public Set<UserRole> getRoles() {
    // begin-user-code
    return roles;
    // end-user-code
  }

  /** 
   * @return the theDAO
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_cRt0EACBEd-3bfn3CXzkSA?GETTER"
   */
  public SecurityManagerDAO getTheDAO() {
    // begin-user-code
    return theDAO;
    // end-user-code
  }

  /** 
   * @return the thePAO
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_exKSAACBEd-3bfn3CXzkSA?GETTER"
   */
  public IxSecurityManagerPAO getThePAO() {
    // begin-user-code
    return thePAO;
    // end-user-code
  }

  /**
   * (non-Javadoc)
   * @see IxLogClient#logEntryPrefix()
   * @generated "sourceid:mmi:///#jmethod^kBase=logEntryPrefix^sign=()QString;[jsrctype^kBase=IxLogClient[jcu^kBase=IxLogClient.java[jpack^kBase=com.jmoss.util[jsrcroot^srcfolder=src[project^id=UML-to-Java Project]]]]]$uml.Operation?INHERITED"
   */
  public String logEntryPrefix() {
    // begin-user-code
    return kName;
    // end-user-code
  }

  /** 
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @param role
   * @return
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_kou_0AB1Ed-3bfn3CXzkSA"
   */
  public boolean removeRole(final UserRole role) {
    // begin-user-code
    boolean removed = roles.remove(role);

    if(removed) {
      // Write the changes out
      role.clear();
      
      theDAO.setRoles(roles);
      theDAO.setUserRoles(userRoles);
      theDAO.setAccessLevels(accessLevels);
      theDAO.save();
    }

    return removed;
    // end-user-code
  }

  /**
   * @param roles
   * @return
   */
  public boolean removeRoles(final Collection<UserRole> roles) {
    boolean removed = this.roles.removeAll(roles);

    if(removed) {
      // Write the changes out
      for(UserRole role:roles) {
        role.clear();
      }
      
      theDAO.setRoles(this.roles);
      theDAO.setUserRoles(userRoles);
      theDAO.setAccessLevels(accessLevels);
      theDAO.save();
    }

    return removed;
  }
  
  /** 
   * @param contextOverrides the contextOverrides to set
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_4X__APiOEd6keveglHf0Tw?SETTER"
   */
  public void setContextOverrides(final boolean contextOverrides) {
    // begin-user-code
    this.contextOverrides = contextOverrides;
    // end-user-code
  }

  /** 
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @param name
   * @return
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_SITtgP70Ed63bfn3CXzkSA"
   */
  public boolean validateUserName(final String name) {
    // begin-user-code
    try {
      return name.length() > 1;
    }
    catch(final NullPointerException e) {
      return false;
    }
    // end-user-code
  }

  /**
   * (non-Javadoc)
   * @see VetoableChangeListener#vetoableChange(PropertyChangeEvent)
   * @generated "sourceid:mmi:///#jmethod^kBase=vetoableChange^sign=(Ljava.beans.PropertyChangeEvent;)V[jbintype^kBase=java.beans.VetoableChangeListener[project^id=UML-to-Java Project]]$uml.Operation?INHERITED"
   */
  public void vetoableChange(PropertyChangeEvent evt) throws PropertyVetoException {
    // begin-user-code
    String name = evt.getPropertyName();
    if(name.equals(kPropertyAvailableUsers)) {
      Object vals[] = (Object[])evt.getNewValue();
      for(int i = 0; i < vals.length; i++) {
        RegisteredUser user = (RegisteredUser)vals[i];
        int x = userRoles.occurencesOf(user);
        if(rolesPerUser == x) {
          throw new PropertyVetoException(String.format("User (%s) is already assigned the maximum number of roles (%d)", user, rolesPerUser), evt);
        }
      }
    }
    // end-user-code
  }

  /**
   * @param user
   * @param category
   * @return
   */
  public boolean viewCategory(final RegisteredUser user, final kbCategory category) {
    AccessPermission p = getPermissions(user, category, kMemberSetFieldContext);
    if(p != null) {
      boolean allowed = checkActions(p.getActions(), kPermissionView);
      if(allowed) {
        allowed = userMayAccess(category.key(), user);
        return allowed;
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
   * @param context
   * @return
   */
  public boolean viewContext(final RegisteredUser user, final kbContext context) {
    AccessPermission p = getPermissions(user, context, kMemberSetFieldContext);
    if(p != null) {
      boolean allowed = checkActions(p.getActions(), kPermissionView);
      if(allowed) {
        allowed = userMayAccess(context.key(), user);
        return allowed;
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
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @param user
   * @param object
   * @return
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_grRgUP73Ed63bfn3CXzkSA"
   */
  public boolean viewAlias(final RegisteredUser user, final kbObject object) {
    // begin-user-code
    AccessPermission p = getPermissions(user, object, kMemberSetFieldAlias);
    if(p != null) {
      boolean allowed = checkActions(p.getActions(), kPermissionView);
      if(allowed) {
        allowed = userMayAccess(object.getObj(), user);
        return allowed;
      }
      else {
        return false;
      }
    }
    else {
      return false;
    }
    // end-user-code
  }

  /** 
   * @param user
   * @param object
   * @return
   */
  public boolean viewPlural(final RegisteredUser user, final kbObject object) {
    AccessPermission p = getPermissions(user, object, kMemberSetFieldPlural);
    if(p != null) {
      boolean allowed = checkActions(p.getActions(), kPermissionView);
      if(allowed) {
        allowed = userMayAccess(object.getObj(), user);
        return allowed;
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
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @param user
   * @param object
   * @return
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_wukLgP78Ed63bfn3CXzkSA"
   */
  public boolean viewHistory(final RegisteredUser user, final kbObject object) {
    // begin-user-code
    AccessPermission p = getPermissions(user, object, kMemberSetFieldHistory);
    if(p != null) {
      boolean allowed = checkActions(p.getActions(), kPermissionView);
      if(allowed) {
        allowed = userMayAccess(object.getObj(), user);
        return allowed;
      }
      else {
        return false;
      }
    }
    else {
      return false;
    }
    // end-user-code
  }

  /** 
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @param user
   * @param object
   * @return
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_Y2m34P78Ed63bfn3CXzkSA"
   */
  public boolean viewIcon(final RegisteredUser user, final kbObject object) {
    // begin-user-code
    AccessPermission p = getPermissions(user, object, kMemberSetFieldIcon);
    if(p != null) {
      boolean allowed = checkActions(p.getActions(), kPermissionView);
      if(allowed) {
        allowed = userMayAccess(object.getObj(), user);
        return allowed;
      }
      else {
        return false;
      }
    }
    else {
      return false;
    }
    // end-user-code
  }

  /** 
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @param user
   * @param object
   * @return
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_ENKusP78Ed63bfn3CXzkSA"
   */
  public boolean viewLocale(final RegisteredUser user, final kbObject object) {
    // begin-user-code
    AccessPermission p = getPermissions(user, object, kMemberSetFieldLocale);
    if(p != null) {
      boolean allowed = checkActions(p.getActions(), kPermissionView);
      if(allowed) {
        allowed = userMayAccess(object.getObj(), user);
        return allowed;
      }
      else {
        return false;
      }
    }
    else {
      return false;
    }
    // end-user-code
  }

  /** 
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @param user
   * @param object
   * @return
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_1AJ8gP78Ed63bfn3CXzkSA"
   */
  public boolean viewOwnership(final RegisteredUser user, final kbObject object) {
    // begin-user-code
    AccessPermission p = getPermissions(user, object, kMemberSetFieldOwnership);
    if(p != null) {
      boolean allowed = checkActions(p.getActions(), kPermissionView);
      if(allowed) {
        allowed = userMayAccess(object.getObj(), user);
        return allowed;
      }
      else {
        return false;
      }
    }
    else {
      return false;
    }
    // end-user-code
  }

  /** 
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @param user
   * @param object
   * @return
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_rrqSUP78Ed63bfn3CXzkSA"
   */
  public boolean viewPhonics(final RegisteredUser user, final kbObject object) {
    // begin-user-code
    AccessPermission p = getPermissions(user, object, kMemberSetFieldPhonics);
    if(p != null) {
      boolean allowed = checkActions(p.getActions(), kPermissionView);
      if(allowed) {
        allowed = userMayAccess(object.getObj(), user);
        return allowed;
      }
      else {
        return false;
      }
    }
    else {
      return false;
    }
    // end-user-code
  }

  /** 
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @param user
   * @param object
   * @return
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_jLUo8P78Ed63bfn3CXzkSA"
   */
  public boolean viewProper(final RegisteredUser user, final kbObject object) {
    // begin-user-code
    AccessPermission p = getPermissions(user, object, kMemberSetFieldProper);
    if(p != null) {
      boolean allowed = checkActions(p.getActions(), kPermissionView);
      if(allowed) {
        allowed = userMayAccess(object.getObj(), user);
        return allowed;
      }
      else {
        return false;
      }
    }
    else {
      return false;
    }
    // end-user-code
  }

  /** 
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @param user
   * @param object
   * @return
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_nQGF8P78Ed63bfn3CXzkSA"
   */
  public boolean viewReferent(final RegisteredUser user, final kbObject object) {
    // begin-user-code
    AccessPermission p = getPermissions(user, object, kMemberSetFieldReferent);
    if(p != null) {
      boolean allowed = checkActions(p.getActions(), kPermissionView);
      if(allowed) {
        allowed = userMayAccess(object.getObj(), user);
        return allowed;
      }
      else {
        return false;
      }
    }
    else {
      return false;
    }
    // end-user-code
  }

  /** 
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @param user
   * @param object
   * @return
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_mI2cEPiQEd6keveglHf0Tw"
   */
  public boolean viewSymbol(final RegisteredUser user, final kbObject object) {
    // begin-user-code
    // Check for any permissions for this user, because the existence of any implies view symbol
    AccessPermission p = getPermissions(user, object);
    if(p != null) {
      boolean allowed = checkActions(p.getActions(), kPermissionView);
      if(allowed) {
        allowed = userMayAccess(object.getObj(), user);
        return allowed;
      }
      else {
        return false;
      }
    }
    else {
      return false;
    }
    // end-user-code
  }

  /** 
   * <!-- begin-UML-doc -->
   * <!-- end-UML-doc -->
   * @param user
   * @param object
   * @return
   * @generated "sourceid:platform:/resource/UML%20Project/Security%20Manager.emx#_fqUAUP78Ed63bfn3CXzkSA"
   */
  public boolean viewType(final RegisteredUser user, final kbObject object) {
    // begin-user-code
    AccessPermission p = getPermissions(user, object, kMemberSetFieldType);
    if(p != null) {
      boolean allowed = checkActions(p.getActions(), kPermissionView);
      if(allowed) {
        allowed = userMayAccess(object.getObj(), user);
        return allowed;
      }
      else {
        return false;
      }
    }
    else {
      return false;
    }
    // end-user-code
  }

  /**
   * Controllers call this to cause all listeners to be notified of the change.
   * The DAO is called to persist the updated user role data.
   * 
   * @param existingRole
   * @param newRole
   * @return
   * @throws Exception
   */
  public boolean updateRole(final UserRole existingRole, final UserRole newRole) throws Exception {  
    boolean removed = roles.remove(existingRole);

    if(removed) {
      roles.add(newRole);
  
      // Have the DAO update the user data
      if(theDAO.update(existingRole, newRole)) {
        
        // Write the changes out immediately
        theDAO.setRoles(roles);
        theDAO.setUserRoles(userRoles);
        theDAO.setAccessLevels(accessLevels);
        
        theDAO.clear();
        theDAO.save();
    
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
   * @param obj
   * @param user
   * @return
   */
  public boolean userMayAccess(final String obj, final RegisteredUser user) {
    return user.getAccessLevel() >= getAccessLevel(obj);
  }

  /**
   * @param contexts
   * @param user
   * @return
   */
  public boolean userMayCreate(final List<kbContext> contexts, final RegisteredUser user) {
    AccessPermission p = getPermissions(user, contexts);
    if(p != null) {
      return checkActions(p.getActions(), kPermissionCreate);
    }
    else {
      return false;
    }
  }
  
  /**
   * @param contexts
   * @param user
   * @return
   */
  public boolean userMayEdit(final List<kbContext> contexts, final RegisteredUser user) {
    AccessPermission p = getPermissions(user, contexts);
    if(p != null) {
      return checkActions(p.getActions(), kPermissionEdit);
    }
    else {
      return false;
    }
  }
  
  /**
   * @param contexts
   * @param user
   * @return
   */
  public boolean userMayDelete(final List<kbContext> contexts, final RegisteredUser user) {
    AccessPermission p = getPermissions(user, contexts);
    if(p != null) {
      return checkActions(p.getActions(), kPermissionDelete);
    }
    else {
      return false;
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
   */
  public void populateSupportedTypes() {
    daoTypes.add(DAO.kMsAccess);
    daoTypes.add(DAO.kMySQL);
    daoTypes.add(DAO.kXML);
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
        SecurityManagerDAO newDAO = newFactory.getSecurityManagerDAO(Constants.kAddAction, theDAO.getRoleFactory(), theDAO.getPermissionFactory());
        newDAO.create();
        
        newDAO.setRoles(theDAO.getRoles());
        newDAO.setUserRoles(theDAO.getUserRoles());
        newDAO.setAccessLevels(theDAO.getAccessLevels());
        newDAO.setIncludeHistory(theDAO.isIncludeHistory());
        
        newDAO.populate();
        
        SecurityManagerDAO oldDAO = theDAO;
        theDAO = newDAO;
        
        // Archive the old data source
        try {
          oldDAO.move();
        }
        catch(final Exception e) {
          AppMgr.logTrace(e);
          ErrorManager.addError(this, String.format("Error (%s) archiving old DAO for (%s)", Java.getMessage(e), newdatasource));
        }
        
        // Ensure all roles are set to the new DAO
        for(UserRole aRole:roles) {
          aRole.setTheDAO(newFactory.getUserRoleDAO());
        }
      }
      catch(final Exception e) {
        AppMgr.logTrace(e);
        ErrorManager.addError(this, String.format("Error (%s) processing new DAO for (%s)", Java.getMessage(e), newdatasource));
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
      SecurityManager theCopy = (SecurityManager)clone();
      theCopy.accessLevels = new Hashtable<>(accessLevels); 
      theCopy.roles = new TreeSet<>(roles);
      theCopy.userRoles = new HashBag("UserRolesCopy", userRoles);
      return theCopy;
    }
    catch(final CloneNotSupportedException e) {
      ErrorManager.addError(this, Java.getMessage(e));
    }
    
    return null;
  }

  /**
   * Creates a copy of the source Manager with a new, temporary DAO without the expense of instantiating a new DAO object.
   * Useful to hold changes to a Manager without modifying the source and before committing any changes.
   * @param daoname
   * @return
   */
  public IxMutableManager mutate(final String daoname) {
    int daotype = DAO.getType(daoname);
    if(daotype != theDAO.getDaoType()) {
      String datasource = AppMgr.createDataSourceNamePrefs(kBase);
      try {
        SecurityManager theCopy = (SecurityManager)copy();
        DAOFactory factory = DAOFactory.getDAOFactoryInstance(daotype, host, datasource, -1, "", "");
        if(factory != null) {
          theCopy.theDAO = factory.getSecurityManagerDAO(Constants.kAddAction, theDAO.getRoleFactory(), theDAO.getPermissionFactory());
          return theCopy;
        }
      }
      catch(final Exception e) {
        AppMgr.logTrace(e);
        ErrorManager.addError(this, String.format("Error (%s) assigning temp DAO for (%s)", Java.getMessage(e), datasource));
      }
    }
    else {
      return this;
    }
    
    return this;
  }

  /**
   * @return
   */
  public static SortedSet<String> getPermissionSet() {
    return permissionSet;
  }

  /**
   * TODO: Generalize - what is the kBase of the problem - and of the solution (some kind of combination)?
   * @return
   */
  public static SortedSet<String> getPermissionCartesianSet() {
    SortedSet<String> permissionSet = new TreeSet<>();
    StringBuilder buf = new StringBuilder();
    for(int i = 1; i < Math.pow(2, 4); i++) {
      if(Utils.testFlag(i, 1)) {
        buf.append(kPermissionCreate);
        buf.append('|');
      }
      
      if(Utils.testFlag(i, 2)) {
        buf.append(kPermissionView);
        buf.append('|');
      }
      
      if(Utils.testFlag(i, 4)) {
        buf.append(kPermissionEdit);
        buf.append('|');
      }
      
      if(Utils.testFlag(i, 8)) {
        buf.append(kPermissionDelete);
        buf.append('|');
      }
      
      if(buf.length() > 0 && buf.charAt(buf.length()-1) == '|') {
        buf.setLength(buf.length()-1);
      }
      
      permissionSet.add(buf.toString());
      buf.setLength(0);
    }
    
    return permissionSet;
  }

  /**
   * @return
   */
  public static SortedSet<String> getFieldLevelPermissionCartesianSet() {
    SortedSet<String> permissionSet = new TreeSet<>();
    StringBuilder buf = new StringBuilder();
    for(int i = 1; i < Math.pow(2, 4); i++) {
      if(Utils.testFlag(i, 2)) {
        buf.append(kPermissionView);
        buf.append('|');
      }
      
      if(Utils.testFlag(i, 4)) {
        buf.append(kPermissionEdit);
        buf.append('|');
      }
      
      if(buf.length() > 0 && buf.charAt(buf.length()-1) == '|') {
        buf.setLength(buf.length()-1);
      }
      
      permissionSet.add(buf.toString());
      buf.setLength(0);
    }
    
    return permissionSet;
  }

  /**
   * @return
   */
  public static SortedSet<String> getFieldSet() {
    return fieldSet;
  }

  /**
   * Accessor
   * @return
   */
  public Map<String,Integer> getAccessLevels() {
    return accessLevels;
  }

  /**
   * @param obj
   * @param doSave
   * @throws PropertyVetoException
   */
  public void removeAccessLevel(final String obj, final boolean doSave) throws PropertyVetoException {
    Integer theLevel = accessLevels.get(obj);
    if(theLevel != null) {
      vetoableChangeSupport.fireVetoableChange(kPropertyAccessLevel, theLevel.intValue(), Constants.kAccessLevelUnclassified);

      // The KB Object had a non-default access level, so remove it from the Map to denote that it is now default
      accessLevels.remove(obj);
      
      if(doSave) {
        theDAO.setAccessLevels(accessLevels);
        theDAO.save();
      }
    }
  }
  
  /**
   * @param obj
   * @param accessLevel
   * @param doSave
   * @throws PropertyVetoException
   */
  public void setAccessLevel(final String obj, final int accessLevel, final boolean doSave) throws PropertyVetoException {
    int oldAccessLevel = accessLevel;
    vetoableChangeSupport.fireVetoableChange(kPropertyAccessLevel, oldAccessLevel, accessLevel);

    if(accessLevel == Constants.kAccessLevelUnclassified) {
      Integer theLevel = accessLevels.get(obj);
      if(theLevel != null) {
        // The KB Object had a non-default access level, so remove it from the Map to denote that it is now default
        accessLevels.remove(obj);
      }
    }
    else {
      // Currently non-default and setting to a non-default
      accessLevels.put(obj, accessLevel);
    }
    
    if(doSave) {
      theDAO.setAccessLevels(accessLevels);
      theDAO.save();
    }
  }

  /**
   * @param obj
   * @return
   */
  public int getAccessLevel(final String obj) {
    Integer accessLevel = accessLevels.get(obj);
    if(accessLevel != null) {
      return accessLevel.intValue();
    }
    else {
      // Default Access Level for KB Objects
      return Constants.kAccessLevelUnclassified;
    }
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
      return String.valueOf(true);
    }
    else {
      return null;
    }
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
   * Based on the filters headers return a representation of the object
   * suitable for display in a table.
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
}
