/*
 * @(#)AWSPlugIn.java	01/24/2020
 *
 * Copyright 2001-2020 Jeffrey Moss All Rights Reserved.
 *
 */

package com.jmoss.plugins;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.*;
import com.amazonaws.services.dynamodbv2.xspec.S;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.jmoss.AppMgr;
import com.jmoss.Constants;
import com.jmoss.data.CommonMutableTreeNode;
import com.jmoss.data.LoginUser;
import com.jmoss.data.ServerDetails;
import com.jmoss.data.UserManager;
import com.jmoss.kb.kbMgr;
import com.jmoss.kb.kbObject;
import com.jmoss.ui.PAO;
import com.jmoss.util.*;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

/**
 *
 * @author  Jeffrey Moss
 */
public final class AWSPlugIn extends AxPlugIn implements ActionListener, IxErrorClient {
  
  // Access from threads
  private static AWSPlugIn instance;

  private static final String AWS_HOME = "console.aws.amazon.com";
  private static final String ACCESS_KEY_ID = "access_key_id";
  private static final String SECRET_KEY_ID = "secret_key_id";

  private static boolean debug = false;

  private UserManager userManager;
  private LoginUser login;
  private String access_key_id;
  private String secret_key_id;
  private BasicAWSCredentials awsCreds;
  private AmazonDynamoDB dynamoDB;
  private String table;

  private Class<?> UIFrame;
  private Class<?> UIFactory;

  /**
   **/
  public AWSPlugIn() {
    super("AWS Plug-In");
    LogMgr.putTrace(getClass(), "AWSPlugIn");

    instance = this;
    description = "Access AWS Services";

    try {
      UIFrame = ClassLoader.getSystemClassLoader().loadClass("com.jmoss.ui.swing.JxFrame");
      UIFactory = ClassLoader.getSystemClassLoader().loadClass("com.jmoss.ui.UIFactory");

      navigation = new CommonMutableTreeNode(this);
      navigation.add(new CommonMutableTreeNode("Select Environment", 0));
      navigation.add(new CommonMutableTreeNode("", 0));
      {
        Regions region = Regions.US_EAST_1;
        CommonMutableTreeNode regionNode = new CommonMutableTreeNode(region, region);
        navigation.add(regionNode);
        regionNode.add(new CommonMutableTreeNode("Apps", region, 0));
        regionNode.add(new CommonMutableTreeNode("Configurations", region, 0));
        CommonMutableTreeNode dataNode = new CommonMutableTreeNode("Data", region);
        regionNode.add(dataNode);
        dataNode.add(new CommonMutableTreeNode("Select Table ...", region, 0));
        dataNode.add(new CommonMutableTreeNode("View Records ...", region, 0));
      }
      {
        Regions region = Regions.US_WEST_2;
        CommonMutableTreeNode regionNode = new CommonMutableTreeNode(region, region);
        navigation.add(regionNode);
        regionNode.add(new CommonMutableTreeNode("Apps", region, 0));
        regionNode.add(new CommonMutableTreeNode("Configurations", region, 0));
        CommonMutableTreeNode dataNode = new CommonMutableTreeNode("Data", region);
        regionNode.add(dataNode);
        dataNode.add(new CommonMutableTreeNode("Select Table ...", region, 0));
        dataNode.add(new CommonMutableTreeNode("View Records ...", region, 0));
      }

      Method createMenu = UIFactory.getMethod("createMenu", CommonMutableTreeNode.class, ActionListener.class);
      theMenuItem = (JMenuItem) createMenu.invoke(UIFactory, navigation, this);

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

    LogMgr.popTrace("AWSPlugIn");
  }

  /**
   **/
  private List<String> getTables(AmazonDynamoDB ddb) {
    if(ddb == null) {
      ddb = AmazonDynamoDBClientBuilder.defaultClient();
    }

    List<String> tables = new ArrayList<>();
    ListTablesRequest request;

    boolean more_tables = true;
    String last_name = null;

    while(more_tables) {
      if(last_name == null) {
        request = new ListTablesRequest()
                .withLimit(10);
      }
      else {
        request = new ListTablesRequest()
                .withLimit(10)
                .withExclusiveStartTableName(last_name);
      }

      ListTablesResult table_list = ddb.listTables(request);
      tables.addAll(table_list.getTableNames());

      last_name = table_list.getLastEvaluatedTableName();
      if(last_name == null) {
        more_tables = false;
      }

      for(String table_name : tables) {
        DescribeTableResult result = ddb.describeTable(table_name);
        TableDescription description = result.getTable();
      }
    }

    return tables;
  }

  /**
   **/
  private DescribeTableResult getTableResult(AmazonDynamoDB ddb, final String table) {
    if(ddb == null) {
      ddb = AmazonDynamoDBClientBuilder.defaultClient();
    }

    return ddb.describeTable(table);
  }

  /**
   **/
  public void execute() {

  }

  /**
   **/
  private boolean init() {
    if(login == null) {
      if(userManager == null) {
        userManager = UserManager.getInstance();
      }

      if(userManager != null) {
        List<LoginUser> loginsByServer = userManager.getLoginsByServer(AWS_HOME);
        if(loginsByServer.size() == 1) {
          login = loginsByServer.get(0);
          if(login == null) {
            try {
              Method getText = UIFactory.getMethod("getText", UIFrame, String.class, String.class, String.class, IxLogClient.class, String.class);
              Method getPassword = UIFactory.getMethod("getPassword", UIFrame, String.class, String.class, String.class, String.class, String.class, Boolean.class, String.class);

              String userName = (String) getText.invoke(UIFactory, null, "Connect to AWS", "Enter User Name:", "", this, getClass().getName());
              // String userName = UIFactory.getText(null, "Connect to AWS", "Enter User Name:", "", this, getClass().getName());
              if(userName != null) {
                String pw = (String) getPassword.invoke(UIFactory, null, "Connect to AWS", "Enter Password:", "", this, getClass().getName());
                // String password = UIFactory.getText(null, "Connect to AWS", "Enter Password:", "", this, getClass().getName());
                if(pw != null) {
                  String host = (String) getText.invoke(UIFactory, null, "Connect to AWS", "Enter Host:", "us-east-1.console.aws.amazon.com/", this, getClass().getName());
                  // String host = UIFactory.getText(null, "Connect to AWS", "Enter Host:", "dev99999.service-now.com", this, getClass().getName());
                  login = new LoginUser(userName, pw, new ServerDetails(host, "elasticbeanstalk/home"));
                }
              }
            }
            catch(Exception ex) {
              String trace = Java.getStackTrace(ex, Constants.NL_SUBSTITUTE, 2, false);
              AppMgr.logError(this, trace);
            }
          }
        }
        else {
          try {
            Method showList = UIFactory.getMethod("showList", UIFrame, Collection.class, boolean.class, String.class);
            int sel = (Integer) showList.invoke(UIFactory, null, loginsByServer, false, getName());
            if(sel >= 0) {
              login = loginsByServer.get(sel);
              access_key_id = (String) login.getNameValue(ACCESS_KEY_ID);
              secret_key_id = (String) login.getNameValue(SECRET_KEY_ID);
              awsCreds = new BasicAWSCredentials(access_key_id, secret_key_id);
            }
          }
          catch(Exception ex) {
            String trace = Java.getStackTrace(ex, Constants.NL_SUBSTITUTE, 2, false);
            AppMgr.logError(this, trace);
          }
        }
      }
    }

    assert awsCreds != null;
    return login != null;
  }

  /**
   * IxTableRow
   * @param header
   * @param mask
   * @return
   */
  @Override
  public Object getByHeader(String header, int mask) {
    if(header == kHostHeader) {
      if(this.host != null) {
        kbObject hostObject = kbMgr.getObjectManager().fetchObject(this.host.toString());
        if(hostObject != null) {
          return hostObject.symbol();
        }
        else {
          return this.host;
        }
      }
      else {
        return kNoHost;
      }
    }
    else {
      return super.getByHeader(header, mask);
    }
  }

  /**
   * @param e
   */
  @Override
  public void actionPerformed(ActionEvent e) {
    if(e.getActionCommand().equals("Select Environment")) {
      LoginUser curr_login = login;
      login = null;
      boolean success = init();
      if(success == false) {
        login = curr_login;
      }

      AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
              .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
              .withRegion(Regions.US_WEST_2).build();
      FeedbackManager.showInfo(s3Client.toString(), true);
    }
    else if(e.getActionCommand().equals("Apps")) {
      boolean success = init();
      if(success) {
      }
    }
    else if(e.getActionCommand().equals("Configurations")) {
      boolean success = init();
      if(success) {
      }
    }
    else if(e.getActionCommand().equals("Select Table ...")) {
      boolean success = init();
      if(success) {
        JMenuItem mi = (JMenuItem) e.getSource();
        Regions region = (Regions) mi.getClientProperty("linkObject");
        AmazonDynamoDBClientBuilder builder = AmazonDynamoDBClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
                .withRegion(region);

        dynamoDB = builder.build();
        List<String> tables = getTables(dynamoDB);
        try {
          Method showList = UIFactory.getMethod("showList", UIFrame, Collection.class, boolean.class, String.class);
          int sel = (Integer) showList.invoke(UIFactory, null, tables, false, getName());
          if(sel >= 0) {
            table = tables.get(sel);
          }
        }
        catch(Exception ex) {
          String trace = Java.getStackTrace(ex, Constants.NL_SUBSTITUTE, 2, false);
          AppMgr.logError(this, trace);
        }
      }
    }
    else if(e.getActionCommand().equals("View Records ...")) {
      boolean success = init();
      if(success) {
        if(table == null) {
          ActionEvent event = new ActionEvent(e.getSource(), 1001, "Select Table ...");
          actionPerformed(event);
        }

        if(table != null) {
          DescribeTableResult tableResult = getTableResult(dynamoDB, table);
          TableDescription description = tableResult.getTable();
          List<AttributeDefinition> definitions = description.getAttributeDefinitions();
          AttributeDefinition att = definitions.get(0);
          String attributeName = att.getAttributeName();

          try {
            List<String> records = new ArrayList<>();
            ScanResult scan = dynamoDB.scan(new ScanRequest(this.table));
            List<Map<String, AttributeValue>> items = scan.getItems();
            for(Map<String, AttributeValue> item : items) {
              records.add(Utils.stringize(item, "", false));
            }

            Method showList = UIFactory.getMethod("showList", UIFrame, String.class, String.class, Collection.class, boolean.class, String.class);
            int sel = (Integer) showList.invoke(UIFactory, null, this.table, description.getTableArn(), records, false, getName());
            if(sel >= 0) {
              FeedbackManager.showInfo(records.get(sel), true);
            }
          }
          catch(Exception ex) {
            FeedbackManager.showException(ex);
          }
        }
        else {
          FeedbackManager.showInfo("No table selected", true);
        }
      }
    }
  }

  /**
   **/
  private void select(Map<String,AttributeValue> key_to_get) {
    GetItemRequest request = new GetItemRequest()
            .withKey(key_to_get)
            .withTableName(this.table);

    try {
      Map<String,AttributeValue> returned_item = dynamoDB.getItem(request).getItem();
      if(returned_item != null) {
        Set<String> keys = returned_item.keySet();
        List<String> records = new ArrayList<>();
        for(String key : keys) {
          records.add(returned_item.get(key).toString());
        }

        try {
          Method showList = UIFactory.getMethod("showList", UIFrame, Collection.class, boolean.class, String.class);
          int sel = (Integer) showList.invoke(UIFactory, null, records, false, getName());
          if(sel >= 0) {
            FeedbackManager.showInfo(records.get(sel), true);
          }
        }
        catch(Exception ex) {
          String trace = Java.getStackTrace(ex, Constants.NL_SUBSTITUTE, 2, false);
          AppMgr.logError(this, trace);
        }
      }
      else {
        FeedbackManager.showInfo("No item found with the key " + name, true);
      }
    }
    catch(AmazonServiceException ex) {
      FeedbackManager.showException(ex);
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
   * Every log entry written by this object starts with a certain prefix after timestamp, id, and thread tags.
   * @return 
   */
  public String errorEntryPrefix() {
    return name;
  }

  /**
   * @param args
   */
  public static void main(String[] args) {
    instance = new AWSPlugIn();    
    instance.execute();
  }
}
