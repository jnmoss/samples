/*
 * @(#)SearchManager.java	 08/01/2010
 *
 * Copyright 1995-2020 Jeffrey Moss All Rights Reserved.
 *
 */

package com.jmoss.util;

import com.jmoss.Accessor;
import com.jmoss.AppMgr;
import com.jmoss.Constants;
import com.jmoss.IxManager;
import com.jmoss.IxMutableManager;
import com.jmoss.data.CommonMutableTreeNode;
import com.jmoss.data.ConnectionFactory;
import com.jmoss.data.ContentSearch;
import com.jmoss.data.DAO;
import com.jmoss.data.DAOException;
import com.jmoss.data.DAOFactory;
import com.jmoss.data.IxPersistent;
import com.jmoss.data.SearchManagerDAO;
import com.jmoss.ui.IxSearchManagerPAO;
import com.jmoss.ui.PAO;
import com.jmoss.ui.PAOFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.naming.event.NamingListener;

import javax.swing.event.HyperlinkListener;

public final class SearchManager extends AxSearchManager implements IxErrorClient, IxPersistent {
  
  public static final String kBase = SearchManagerDAO.kBase;

  private static final String kName = "Common Search Manager";  
  
  private static final int kDefaultDAO = DAO.kTypeXML;
  private static final int kDefaultPAO = PAO.kTypeSwing;

  private List<ContentSearch> searches;
  private Map<ContentSearch,Boolean> persistent;
  
  private String host;
  private SearchManagerDAO theDAO;
  private IxSearchManagerPAO thePAO;  

  private List<TrashEntry> trash = new ArrayList<>();
  
  private static SearchManager instance;

  /**
   * @throws ClassNotFoundException
   * @throws IllegalAccessException
   * @throws InstantiationException
   * @throws DAOException
   */
  public SearchManager() throws ClassNotFoundException, IllegalAccessException, InstantiationException, DAOException {
    // begin-user-code
    LogMgr.putTrace(getClass(), "SearchManager");
    
    int daotype = Integer.valueOf(AppMgr.getProperty(Constants.kPreference, String.format("data.dao.type.%s", kName), String.valueOf(kDefaultDAO))).intValue();
    host = AppMgr.getProperty(Constants.kPreference, String.format("data.dao.host.%s", kName), ConnectionFactory.kDefaultServer);
    
    String datasource = AppMgr.createDataSourceNamePrefs(kBase);
    AppMgr.logEvent(this, String.format("Retrieving search results from data source (%s/%s)...", host, datasource));
    
    // Set the instance as early as possible to allow other objects (like a DAO)
    // to help build the SearchManager, being that they are called from the ctor
    instance = this;
    
    DAOFactory daoFactory = DAOFactory.getDAOFactory(daotype, host, datasource, -1, "", "", false);
    if(daoFactory != null) {
      // Register this component as a user of the DAO type
      AppMgr.registerDaoClient(toString(), daotype);
    
      // Register the factory for Searches
      AppMgr.registerDaoFactory(kName, daoFactory);
      theDAO = daoFactory.getSearchManagerDAO();
      
      if(searches == null) {
        searches = theDAO.getSearches();
      }
    }
    else {
      throw new IllegalArgumentException(DAO.kUnknown);
    }
    
    int paotype = Integer.valueOf(AppMgr.getProperty(Constants.kPreference, String.format("com.jmoss.ui.pao.type.%s", kName), String.valueOf(kDefaultPAO))).intValue();
    PAOFactory paoFactory = PAOFactory.getPAOFactory(paotype);
    if(paoFactory != null) {
      thePAO = paoFactory.getSearchManagerPAO(kName);
    }
    else {
      throw new IllegalArgumentException(PAO.kUnknown);
    }
    
    populateSupportedTypes();
    
    LogMgr.popTrace("SearchManager");
    // end-user-code
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
   * @param domain
   * @param nl
   * @param hll
   * @param client
   * @return
   * @throws LoggableException
   */
  public boolean searchNew(final String domain, final NamingListener nl, final HyperlinkListener hll, final IxErrorClient client) throws LoggableException {
    return thePAO.searchNew(domain, nl, hll, client);
  }

  /**
   * @param theSearch
   * @param domain
   * @param nl
   * @param hll
   * @param client
   * @return
   * @throws LoggableException
   */
  public boolean search(final ContentSearch theSearch, final String domain, final NamingListener nl, final HyperlinkListener hll, final IxErrorClient client) throws LoggableException {
    return thePAO.search(theSearch, domain, nl, hll, client);
  }

  /**
   * @param theSearch
   * @param nl
   * @param hll
   * @param client
   */
  public void show(final ContentSearch theSearch, final NamingListener nl, final HyperlinkListener hll, final IxErrorClient client) {
    thePAO.show(theSearch, nl, hll, client);
  }

  /**
   * @param s
   * @return
   */
  public boolean addSavedSearch(final ContentSearch s) {
    if(persistent == null) {
      persistent = new Hashtable<ContentSearch,Boolean>();
    }
    
    persistent.put(s, true);
    return addSessionSearch(s);    
  }
  
  /**
   * @param s
   * @return
   */
  public boolean addSessionSearch(final ContentSearch s) {
    if(searches == null) {
      searches = new ArrayList<ContentSearch>();
    }
    else {
      if(searches.contains(s)) {
        return false;
      }
    }
    
    boolean added = searches.add(s);
    return added;    
  }

  /**
   * Controllers call this to cause all listeners to be notified of the change.
   * The DAO is called to persist the deleted search data.
   * @param s
   * @return
   * @throws Exception
   */
  public boolean remove(final Search s) throws Exception {
    boolean removed = searches.remove(s);

    if(removed) {
      theDAO.delete(s);  
      theDAO.save();
      return true;
    }
    else {
      return false;
    }
  }

  /**
   * Controllers call this to cause all listeners to be notified of the change.
   * The DAO is called to persist the deleted search data.
   * @param searches
   * @return
   * @throws Exception
   */
  public boolean remove(final Collection<Search> searches) throws Exception {
    boolean removed = this.searches.removeAll(searches);

    if(removed) {
      for(Search aSearch:searches) {
        theDAO.delete(aSearch);
      }
  
      theDAO.save();
      return true;
    }
    else {
      return false;
    }
  }
  
  /**
   * @param direction
   * @param p
   * @param t
   * @param rule
   * @return 
   */
  public boolean addTrash(final TriMorph direction, final CommonMutableTreeNode p, final String t, final String rule) {
    if(true) {
      final TrashEntry entry = new TrashEntry(direction, p, t, rule);
      return trash.add(entry);
    }
    else {
      return false;
    }
  }

  /**
   * @param domain
   * @return
   */
  public List<ContentSearch> getSearches(final String domain) {
    List<ContentSearch> list = new ArrayList<>();
    if(searches != null) {
      for(ContentSearch s:searches) {
        if(Utils.equals(s.getDomain(), domain)) {
          list.add(s);
        }
      }
    }
    
    return list;
  }

  /**
   * @return
   */
  @Accessor
  public final List<TrashEntry> getTrash() {
    return trash;
  }

  /**
   * IxMutableManager
   * @param oldtype
   * @param newtype
   * @return
   */
  public void changeDAO(int oldtype, int newtype) {
    if(oldtype != newtype) {
      // Get parameters for retrieving DAO Factories for both the old and new data sources
      host = AppMgr.getProperty(Constants.kPreference, String.format("data.dao.host.%s", kName), ConnectionFactory.kDefaultServer);
      String newdatasource = AppMgr.createDataSourceNamePrefs(kBase);
      
      // Create the new data source
      try {
        DAOFactory newFactory = DAOFactory.getDAOFactory(newtype, host, newdatasource, -1, "", "", false);
        SearchManagerDAO newDAO = newFactory.getSearchManagerDAO();
        newDAO.create();
        
        if(theDAO.getSearches() != null) {
          newDAO.setSearches(theDAO.getSearches());
          newDAO.populate();
        }
        
        SearchManagerDAO oldDAO = theDAO;
        theDAO = newDAO;
        
        // Archive the old data source
        try {
          oldDAO.move();
        }
        catch(final Exception e) {
          AppMgr.logTrace(e);
          ErrorManager.addError(this, String.format("Error (%s) archiving old DAO for (%s)", Java.getMessage(e), newdatasource));
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
      IxMutableManager theCopy = (IxMutableManager)clone();
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
   * @param daoname
   * @return
   */
  public IxMutableManager mutate(String daoname) {
    int daotype = DAO.getType(daoname);
    if(daotype != theDAO.getDaoType()) {
      String newdatasource = AppMgr.createDataSourceNamePrefs(kBase);
      try {
        SearchManager theCopy = (SearchManager)clone();
        DAOFactory factory = DAOFactory.getDAOFactoryInstance(daotype, host, newdatasource, -1, "", "");
        if(factory != null) {
          theCopy.theDAO = factory.getSearchManagerDAO();
          return theCopy;
        }
      }
      catch(final Exception e) {
        AppMgr.logTrace(e);
        ErrorManager.addError(this, String.format("Error (%s) assigning temp DAO for (%s)", Java.getMessage(e), newdatasource));
      }
    }
    
    return this;
  }

  /**
   * @return
   */
  public static SearchManager getInstance() {
    return instance;
  }

  /**
   * IxLogClient
   * Dump the contents of the object to a newline-delimited string.
   * @param verbose
   * @return
   */
  public String dump(int verbose) {
    return kName;
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
   * IxManager
   * @return
   */
  public String getName() {
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
   * IxPersistent
   * @return
   */
  public void clear() {
    theDAO.setSearches(searches);
    theDAO.clear();
  }

  /**
   * IxPersistent
   * @return
   */
  public boolean load() {
    theDAO.setSearches(searches);
    return theDAO.load();
  }

  /**
   * IxPersistent
   * @return
   */
  public boolean save() {
    for(ContentSearch aSearch:searches) {
      if(persistent.get(aSearch).booleanValue()) {
        theDAO.addSearch(aSearch);
      }
    }
    
    return theDAO.save();
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
  public List<ContentSearch> getSearches() {
    return searches;
  }

  /**
   * @return
   */
  public SearchManagerDAO getTheDAO() {
    return theDAO;
  }

  /**
   * IxTableRow
   * @param header
   * @param mask
   * @return
   */
  public Object getByHeader(final String header, int mask) {
    if(header == kComponentHeader) {
      return this;
    }
    else if(header == kDaoHeader) {
      return DAO.kTypes[theDAO.getType()];
    }
    else if(header == kHostHeader) {
      return this.host;
    }
    else if(header == kHistoryHeader) {
      return theDAO.isIncludeHistory();
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
   * Based on the filters headers return a representation of the object suitable for display in a table.
   * @param currentHeaders
   * @param mask
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
