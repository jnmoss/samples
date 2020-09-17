package servlet;

import com.jmoss.AppMgr;
import com.jmoss.Constants;
import com.jmoss.data.DAO;
import com.jmoss.data.DB;
import com.jmoss.data.NameValuePair;
import com.jmoss.data.RegisteredUser;
import com.jmoss.util.*;
import org.json.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.*;
import java.util.List;

@WebServlet(
  name = "DictionaryServlet",
  urlPatterns = {"/dictionary"}
)
public class DictionaryServlet extends HttpServlet {

  private static final String DELIMITER = Net.encodeURL(Constants.kSymbolBoxDoubleHorizontal);

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    String pstring = req.getQueryString();
    StringBuilder buf = new StringBuilder();

    if(pstring != null) {
      pstring = Net.decodeURL(pstring);
      System.out.printf("%n%s: The query string is: %s%n", "DictionaryServlet", pstring);

      String string = Text.rightOf(pstring, "=", 1, "");
      SearchProfile profile = new SearchProfile(string);

      RegisteredUser user = AppMgr.getUserManager().getActiveUser();
      String dbUrl = String.format("jdbc:mysql://127.0.0.1:3306/Entries?user=%s&password=%s", user.getUserName(), user.getPassword());
      String dbClass = "com.mysql.jdbc.Driver";
      String query = "";
      try {
        Class.forName(dbClass).newInstance();
        Connection con = DriverManager.getConnection(dbUrl);
        Statement stmt = con.createStatement();

        if(profile.isWholeWord()) {
          if(profile.isCaseSensitive()) {
            query = String.format("SELECT word,definition,wordtype FROM Entries WHERE wordtype LIKE '%cn.%c' AND %s", '%', '%', DB.wholeWord("BINARY word", string, DAO.kMySQL, true));
          }
          else {
            query = String.format("SELECT word,definition,wordtype FROM Entries WHERE wordtype LIKE '%cn.%c' AND %s", '%', '%', DB.wholeWord("word", string, DAO.kMySQL, false));
          }
        }
        else if(profile.isPhrase()) {
          if(profile.isCaseSensitive()) {
            query = String.format("SELECT word,definition,wordtype FROM Entries WHERE wordtype LIKE '%cn.%c' AND %s", '%', '%', DB.wholeWord("BINARY word", string, DAO.kMySQL, true));
          }
          else {
            query = String.format("SELECT word,definition,wordtype FROM Entries WHERE wordtype LIKE '%cn.%c' AND %s", '%', '%', DB.wholeWord("word", string, DAO.kMySQL, false));
          }
        }
        else if(profile.isRegex()) {
          try {
            if(profile.isCaseSensitive()) {
              query = String.format("SELECT word,definition,wordtype FROM Entries WHERE wordtype LIKE '%cn.%c' AND (BINARY word REGEXP '%s')", '%', '%', string);
            }
            else {
              query = String.format("SELECT word,definition,wordtype FROM Entries WHERE wordtype LIKE '%cn.%c' AND (word REGEXP '%s')", '%', '%', string);
            }
          }
          catch(final Exception ex) {
            ex.printStackTrace();
          }
        }
        else if(profile.isSoundex()) {
          // TODO: Configure fuzzy - _latin1 encoding in MySQL assumed
          query = String.format("SELECT word,definition,wordtype FROM Entries WHERE wordtype LIKE '%cn.%c' AND SOUNDEX(_latin1'%s')=SOUNDEX(word)", '%', '%', string);
        }
        else if(profile.isWebSearch()) {
          List<String> strings = Text.tokenize(string, " ", null);
          if(profile.isCaseSensitive()) {
            query = String.format("SELECT word,definition,wordtype FROM Entries WHERE wordtype LIKE '%cn.%c' AND BINARY word LIKE '%c%s%c'", '%', '%', '%', strings.get(0), '%');
            for(int i = 1; i < strings.size(); i++) {
              query += String.format(" OR (wordtype LIKE '%cn.%c' AND BINARY word LIKE '%c%s%c')", '%', '%', '%', strings.get(i), '%');
            }
          }
          else {
            query = String.format("SELECT word,definition,wordtype FROM Entries WHERE wordtype LIKE '%cn.%c' AND word LIKE '%c%s%c'", '%', '%', '%', strings.get(0), '%');
            for(int i = 1; i < strings.size(); i++) {
              query += String.format(" OR (wordtype LIKE '%cn.%c' AND word LIKE '%c%s%c')", '%', strings.get(i), '%', '%', '%');
            }
          }
        }
        else {
          if(profile.isCaseSensitive()) {
            query = String.format("SELECT word,definition,wordtype FROM Entries WHERE wordtype LIKE '%cn.%c' AND BINARY word LIKE '%c%s%c'", '%', '%', '%', string, '%');
          }
          else {
            query = String.format("SELECT word,definition,wordtype FROM Entries WHERE wordtype LIKE '%cn.%c' AND word LIKE '%c%s%c'", '%', '%', '%', string, '%');
          }
        }

        ResultSet rs = stmt.executeQuery(query);
        System.out.printf("%s: %s%n", "DictionaryServlet", query);
        while(rs.next()) {
          JSONObject json = new JSONObject();
          json.put(AppMgr.getSharedResString(Constants.kNameLabel), rs.getString("word"));
          json.put(AppMgr.getSharedResString(Constants.kDefinitionLabel), rs.getString("definition"));
          json.put(AppMgr.getSharedResString(Constants.kTypeLabel), rs.getString("wordtype"));
          buf.append(json.toString());
          buf.append(DELIMITER);
        }

        if(buf.length() > DELIMITER.length()) {
          buf.setLength(buf.length() - DELIMITER.length());
        }

        System.out.printf("%s: %s%n", "DictionaryServlet", buf);

        con.close();
      }
      catch(Exception|Error ex) {
        ex.printStackTrace();
      }
    }

    ServletOutputStream out = resp.getOutputStream();
    out.write(buf.toString().getBytes());
    out.flush();
    out.close();
  }
}