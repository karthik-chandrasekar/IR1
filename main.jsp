<html>
<head>
<title>Search Engine </title>
</head>
<body>
<h1>Search Engine</h1>
<ul>
<li><p><b>Search Query:</b>
   <%= request.getParameter("sQuery")%>
</p></li>

<li><p><b>Page Rank:</b>
   <%= request.getParameter("PR")%>
</p></li>
<li><p><b>Authorities and Hubs:</b>
   <%= request.getParameter("AH")%>
</p></li>
<li><p><b>Vector Space Similarity:</b>
   <%= request.getParameter("VS")%>
</p></li>
</ul>
</body>
</html>
