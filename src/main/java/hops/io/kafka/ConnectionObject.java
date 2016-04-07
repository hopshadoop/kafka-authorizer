package hops.io.kafka;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import kafka.security.auth.Acl;

/**
 *
 * @author misdess
 */
public class ConnectionObject {

    private static Connection conn = null;
    PreparedStatement prepStatements;

    static final Logger CONNECTIONLOGGGER
            = Logger.getLogger(ConnectionObject.class.getName());

    //Sql preparedStatements
    final String getAllAclsQuery = "SELECT * from topic_acls";
    final String getTopicAclQuery = "SELECT * from topic_acls where topic_name =?";
    final String getPrincipalAclQuery = "SELECT * from topic_acls where project_name =?";
    final String getPrincipalAcls = "SELECT * from topic_acls where project_name =?";
    final String getProjectId = "SELECT * from project_topics where topic_name =?";
    final String getProjectName = "SELECT *  from project where id =?";
    final String getProjectTeams = "SELECT * from project_team";
    final String getProjects = "SELECT id, projectname from project";
    final String getUsers = "SELECT username, email from users";

    final String insertTopicAcl = "INSERT into topic_acls values()";
    final String insertAcl = "INSERT into topic_acls (topic_name, project_name, "
            + "role, operation_type, permission_type, host) values(?, ?, ?, ?, ?, ?)";

    final String deleteTopicAcls = "DELETE from topic_acls where topic_name =?"
            + " AND project_name =? AND role=? AND operation_type=? AND"
            + " permission_type=? AND host=?";
    final String deleteAllTopicAcls = "DELETE from topic_acls where topic_name =?";

    public ConnectionObject(String dbType) {

        //load the properties file ndb.props and read the values, print a log incase error
        try {
            if (dbType.equalsIgnoreCase("mysql")) {
                Class.forName("com.mysql.jdbc.Driver");
                conn
                        = DriverManager.getConnection("database url");
            }
        } catch (SQLException | ClassNotFoundException ex) {
            CONNECTIONLOGGGER.log(Level.SEVERE, null, ex.toString());
        }

    }

    public void addTopicAcls(String topicName, Set<Acl> acls) {


        /*Load tables user, project and project_team, to access the role of 
        the user. Each acl has a KafkaPrincipal from which we can the project
        name and the user name. Get the project id for this project name and the
        user email for this user name. Using the project id and the user name, 
        get the role of the member in the project.Keeping a cache of 3 tables 
        locally avoids many database read operations.  
         */
        Map<String, String> users = new HashMap<>();
        Map<String, String> projects = new HashMap<>();
        Map<String, String> projectTeams = new HashMap<>();

        ResultSet resultSet;

        try {
            prepStatements = conn.prepareStatement(getProjects);
            resultSet = prepStatements.executeQuery();
            while (resultSet.next()) {
                projects.put(resultSet.getString("projectname"), resultSet.getString("id"));
            }

            prepStatements = conn.prepareStatement(getUsers);
            resultSet = prepStatements.executeQuery();
            while (resultSet.next()) {
                users.put(resultSet.getString("username"), resultSet.getString("email"));
            }

            String projectId__;//projectid_underscore_underscore
            String teamMember;
            String teamRole;
            prepStatements = conn.prepareStatement(getProjectTeams);
            resultSet = prepStatements.executeQuery();
            while (resultSet.next()) {
                projectId__ = resultSet.getString("project_id") + "__";
                teamMember = resultSet.getString("team_member");
                teamRole = resultSet.getString("team_role");

                projectTeams.put(projectId__ + teamMember, teamRole);
            }
        } catch (SQLException ex) {
            CONNECTIONLOGGGER.log(Level.SEVERE, null, ex.toString());
        }

        //add the acls to the database, lookup in the tables above for user role
        String projectName = getProjectName(topicName);
        String projectName__userName;
        String projectId, teamMemberEmail;
        String role;
        try {

            prepStatements = conn.prepareStatement(insertTopicAcl);
            for (Acl acl : acls) {
                projectName__userName = acl.principal().getName();
                projectId = projects.get(projectName__userName.split("__", 2)[0]);
                teamMemberEmail = users.get(projectName__userName.split("__", 2)[1]);
                role = projectTeams.get(projectId + "__" + teamMemberEmail);

                prepStatements.setString(1, topicName);
                prepStatements.setString(2, projectName);

                prepStatements.setString(3, role);

                prepStatements.setString(4, acl.operation().name());
                prepStatements.setString(5, acl.permissionType().name());
                prepStatements.setString(6, acl.host());
                prepStatements.execute();
            }
        } catch (SQLException ex) {
            CONNECTIONLOGGGER.log(Level.SEVERE, null, ex.toString());
        }
    }

    public ResultSet getAllAcls() {

        ResultSet result = null;

        try {

            prepStatements = conn.prepareStatement(getAllAclsQuery);
            result = prepStatements.executeQuery();

        } catch (SQLException ex) {
            CONNECTIONLOGGGER.log(Level.SEVERE, null, ex.toString());
        }
        return result;
    }

    public ResultSet getTopicAcls(String topicName) {

        ResultSet result = null;
        try {
            prepStatements = conn.prepareStatement(getTopicAclQuery);
            prepStatements.setString(1, topicName);
            result = prepStatements.executeQuery();
        } catch (SQLException ex) {
            CONNECTIONLOGGGER.log(Level.SEVERE, null, ex.toString());
        }
        return result;
    }

    public ResultSet getPrinipalAcls(String principalName) {

        ResultSet result = null;
        try {
            prepStatements = conn.prepareStatement(getPrincipalAclQuery);
            prepStatements.setString(1, principalName);
            result = prepStatements.executeQuery();
        } catch (SQLException ex) {
            CONNECTIONLOGGGER.log(Level.SEVERE, null, ex.toString());
        }
        return result;
    }

    public Boolean topicExists(String topicName) {

        Boolean topicExists = false;
        try {
            prepStatements = conn.prepareStatement(getTopicAclQuery);
            prepStatements.setString(1, topicName);
            ResultSet result = prepStatements.executeQuery();
            //if next returns true, that means the topic exists
            topicExists = result.next();
        } catch (SQLException ex) {
            CONNECTIONLOGGGER.log(Level.SEVERE, null, ex.toString());
        }
        return topicExists;
    }

    public Boolean removeTopic(String topicName) {

        Boolean aclRemoved = false;
        try {
            prepStatements = conn.prepareStatement(deleteAllTopicAcls);
            prepStatements.setString(1, topicName);
            aclRemoved = prepStatements.execute();
        } catch (SQLException ex) {
            CONNECTIONLOGGGER.log(Level.SEVERE, null, ex.toString());
        }
        return aclRemoved;
    }

    public Boolean removeAcls(String topicName, Set<Acl> acls) {

        Boolean alcsRemoved = false;
        String projectName = getProjectName(topicName);
        try {

            prepStatements = conn.prepareStatement(deleteTopicAcls);
            for (Acl acl : acls) {
                prepStatements.setString(1, topicName);
                prepStatements.setString(2, projectName);
                prepStatements.setString(3, "data owner");
                prepStatements.setString(4, acl.operation().name());
                prepStatements.setString(5, acl.permissionType().name());
                prepStatements.setString(6, acl.host());
                alcsRemoved = prepStatements.execute();
            }
        } catch (SQLException ex) {
            CONNECTIONLOGGGER.log(Level.SEVERE, null, ex.toString());
        }
        return alcsRemoved;
    }

    private String getProjectName(String topicName) {

        String projectId = null;
        String projectName = null;
        try {

            prepStatements = conn.prepareStatement(getProjectId);
            prepStatements.setString(1, topicName);
            ResultSet resultSet = prepStatements.executeQuery();
            while (resultSet.next()) {
                projectId = resultSet.getString("id");
            }

            prepStatements = conn.prepareStatement(getProjectName);
            prepStatements.setString(1, projectId);
            resultSet = prepStatements.executeQuery();
            while (resultSet.next()) {
                projectName = resultSet.getString("projectname");
            }
        } catch (SQLException ex) {
            CONNECTIONLOGGGER.log(Level.SEVERE, null, ex.toString());
        }
        return projectName;
    }

    public boolean isTopicBelongsToProject(String projectName, String topicName) {

        String projectId = null;

        try {

            prepStatements = conn.prepareStatement("SELECT id from project where projectname=?");
            prepStatements.setString(1, projectName);
            ResultSet resultSet = prepStatements.executeQuery();
            while (resultSet.next()) {
                projectId = resultSet.getString("id");
            }

            prepStatements = conn.prepareCall("SELECT * from project_topics where topic_name=? AND project_id=?");
            prepStatements.setString(1, topicName);
            prepStatements.setString(2, projectId);

            resultSet = prepStatements.executeQuery();
            if (resultSet.next() == false) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }

        return true;
    }

    public String getPrinciplaRole(String projectName__userName) {

        String role = null;

        String projectName = projectName__userName.split("__", 2)[0];
        String userName = projectName__userName.split("__", 2)[1];

        String projectId;
        String email;

        try {
            prepStatements = conn.prepareCall("SELECT from project where projectname=?");
            ResultSet resutlSet = prepStatements.executeQuery();
            while (resutlSet.next()) {
                projectId = resutlSet.getString("id");

            }
            prepStatements = conn.prepareCall("SELECT from user where username=?");
            resutlSet = prepStatements.executeQuery();
            while (resutlSet.next()) {
                email = resutlSet.getString("username");

            }

        } catch (Exception e) {
        }

        return role;
    }

    public void closeConnection() {
        try {
            conn.close();
        } catch (SQLException ex) {
            CONNECTIONLOGGGER.log(Level.SEVERE, null, ex.toString());
        }
    }
}
