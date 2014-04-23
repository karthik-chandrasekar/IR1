<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
  pageEncoding="ISO-8859-1"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title>Search ASU website</title>
</head>

<body>
<form action="KarthikProject3Servlet" method="GET">
<input type="checkbox" name="PR" value="PR" /> Page Rank
<input type="checkbox" name="AH"  value = "AH"/> Authorities and Hubs
<input type="checkbox" name="VS" value = "VS" /> Vector Similarity<br>
<input type="checkbox" name="QE" value = "QE" /> Query Elaboration<br>
Enter your query<input type="text" name="Query"/>
<input type="submit" value="Submit" />
</form>
 ${requestScope.result}
 
</body>
</html> 
