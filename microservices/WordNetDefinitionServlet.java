package servlet;

import com.jmoss.AppMgr;
import com.jmoss.Constants;
import com.jmoss.data.DAO;
import com.jmoss.data.DB;
import com.jmoss.data.RegisteredUser;
import com.jmoss.util.Net;
import com.jmoss.util.SearchProfile;
import com.jmoss.util.Text;
import org.json.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

@WebServlet(
  name = "WordNetDefinitionServlet",
  urlPatterns = {"/wordnet/definition"}
)
public class WordNetDefinitionServlet extends HttpServlet {

  private static final String DELIMITER = Net.encodeURL(Constants.kSymbolBoxDoubleHorizontal);

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    String pstring = req.getQueryString();
    StringBuilder buf = new StringBuilder();

    if(pstring != null) {
      pstring = Net.decodeURL(pstring);
      System.out.printf("%n%s: The query string is: %s%n", "WordNetDefinitionServlet", pstring);

      String text = Text.rightOf(pstring, "=", 1, pstring);
      RegisteredUser user = AppMgr.getUserManager().getActiveUser();
      String dbUrl = String.format("jdbc:mysql://127.0.0.1:3306/Entries?user=%s&password=%s", user.getUserName(), user.getPassword());
      String dbClass = "com.mysql.jdbc.Driver";
      String query = "";
      try {
        Class.forName(dbClass).newInstance();
        Connection con = DriverManager.getConnection(dbUrl);
        Statement stmt = con.createStatement();

        SearchProfile profile = new SearchProfile(text);
        if(profile.isWholeWord()) {
          if(profile.isCaseSensitive()) {
            query = String.format("SELECT lemma,sensenum,definition FROM dict WHERE %s AND pos='n' ORDER BY pos,sensenum", DB.wholeWord("BINARY lemma", text, DAO.kMySQL, true));
          }
          else {
            query = String.format("SELECT lemma,sensenum,definition FROM dict WHERE %s AND pos='n' ORDER BY pos,sensenum", DB.wholeWord("lemma", text, DAO.kMySQL, true));
          }
        }
        else if(profile.isPhrase()) {
          if(profile.isCaseSensitive()) {
            query = String.format("SELECT lemma,sensenum,definition FROM dict WHERE %s AND pos='n' ORDER BY pos,sensenum", DB.wholeWord("BINARY lemma", text, DAO.kMySQL, true));
          }
          else {
            query = String.format("SELECT lemma,sensenum,definition FROM dict WHERE %s AND pos='n' ORDER BY pos,sensenum", DB.wholeWord("lemma", text, DAO.kMySQL, true));
          }
        }
        else if(profile.isRegex()) {
          try {
            if(profile.isCaseSensitive()) {
              query = String.format("SELECT lemma,sensenum,definition FROM dict WHERE (BINARY lemma REGEXP '%s') AND pos='n' ORDER BY pos,sensenum", text);
            }
            else {
              query = String.format("SELECT lemma,sensenum,definition FROM dict WHERE (lemma REGEXP '%s') AND pos='n' ORDER BY pos,sensenum", text);
            }
          }
          catch(final Exception ex) {
            ex.printStackTrace();
          }
        }
        else if(profile.isSoundex()) {
          // TODO: Configure fuzzy - _latin1 encoding in MySQL assumed
          query = String.format("SELECT lemma,sensenum,definition FROM dict WHERE SOUNDEX(_latin1'%s')=SOUNDEX(lemma) AND pos='n' ORDER BY pos,sensenum", text);
        }
        else if(profile.isWebSearch()) {
          List<String> strings = Text.tokenize(text, " ", null);
          if(profile.isCaseSensitive()) {
            query = String.format("SELECT lemma,sensenum,definition FROM dict WHERE BINARY lemma LIKE '%c%s%c' AND pos='n' ORDER BY pos,sensenum", '%', strings.get(0), '%');
            for(int i = 1; i < strings.size(); i++) {
              query += String.format(" OR BINARY lemma LIKE '%c%s%c'", '%', strings.get(i), '%');
            }
          }
          else {
            query = String.format("SELECT lemma,sensenum,definition FROM dict WHERE lemma LIKE '%c%s%c' AND pos='n' ORDER BY pos,sensenum", '%', strings.get(0), '%');
            for(int i = 1; i < strings.size(); i++) {
              query += String.format(" OR lemma LIKE '%c%s%c'", '%', strings.get(i), '%');
            }
          }
        }
        else {
          if(profile.isCaseSensitive()) {
            query = String.format("SELECT lemma,sensenum,definition FROM dict WHERE BINARY lemma LIKE '%c%s%c' AND pos='n' ORDER BY pos,sensenum", '%', text, '%');
          }
          else {
            query = String.format("SELECT lemma,sensenum,definition FROM dict WHERE lemma LIKE '%c%s%c' AND pos='n' ORDER BY pos,sensenum", '%', text, '%');
          }
        }

        ResultSet rs = stmt.executeQuery(query);
        System.out.printf("%s: %s%n", "WordNetDefinitionServlet", query);
        while(rs.next()) {
          JSONObject json = new JSONObject();
          json.put(AppMgr.getSharedResString(Constants.kNumberLabel), rs.getInt("sensenum"));
          json.put(AppMgr.getSharedResString(Constants.kNameLabel), rs.getString("lemma"));
          json.put(AppMgr.getSharedResString(Constants.kDefinitionLabel), rs.getString("definition"));
          buf.append(json.toString());
          buf.append(DELIMITER);
        }

        if(buf.length() > DELIMITER.length()) {
          buf.setLength(buf.length() - DELIMITER.length());
        }

        System.out.printf("%s: %s%n", "WordNetDefinitionServlet", buf);

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