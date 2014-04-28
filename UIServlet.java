package KarthikProject3Servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import edu.asu.irs13.SearchFiles;

/**
 * Servlet implementation class KarthikProject3Servlet
 */
public class KarthikProject3Servlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
       
    /**
     * @see HttpServlet#HttpServlet()
     */
    public KarthikProject3Servlet() {
        super();
        // TODO Auto-generated constructor stub
    }

    /**
     * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // TODO Auto-generated method stub
        PrintWriter out = response.getWriter();
        out.println("Inside Sunil Servlet");
        
        String PR = request.getParameter("PR");
        String AH = request.getParameter("AH");
        String VS = request.getParameter("VS");
        String QE = request.getParameter("QE");
        String Query = request.getParameter("Query");
        String method = "";
        StringBuilder outputBuf = new StringBuilder();
        String filePath = "";
        
        List<Map<String, String>> resultMapList;
        
        if (Query == null)
        {
            out.println("Please enter the query");
        }
        else
        {
        
            if(PR != null)
            {
                method = PR;
            }
            else if (AH != null)
            {
                method = AH;
            }
            else if (VS != null)
            {
                method = VS;
            }       
            
            
            try
            {
                SearchFiles sObj = new SearchFiles();

                //Set query elaboration flag is it is checked by user
                int QEval=0;
                
                if(QE !=null)
                {
                    QEval = 1;
                }
                
                
                out.println("Query " + Query + " Method " + method + " QEval " + QEval);
                //results = sObj.servletCall(Query, method, QEval);
                resultMapList = sObj.servletCall(Query, method, QEval);
                
                System.out.println("Got map returned by search files");
                System.out.println(resultMapList);
                
                outputBuf.append("<html>");
                outputBuf.append("<body>");

                /***for(String result : results)
                {
                    filePath = result;
                    //outputBuf.append("<a href="+filePath+">" + result + "</a>" + "<br>");
                    outputBuf.append("<p>"+result+"</p1>");
                }***/
                
                String para;
                String resultLink;
                String qe;
                String DYMWord;
                
                for(Map<String, String> resultPair : resultMapList)
                {
                    para = null;
                    resultLink = null;
                    qe = null;
                    DYMWord = null;
                    
                    if(resultPair.containsKey("DID"))
                    {
                        if(resultPair.get("DID").equals("-1"))
                        {
                        }
                        else
                        {
                            para = resultPair.get("DID");
                            para = para + " - ";
                        }
                    }
                    
                    if(resultPair.containsKey("CID"))
                    {
                        if(para == null)
                        {
                            para = resultPair.get("CID");
                        }
                        else
                        {
                            para = para + "  " + resultPair.get("CID");
                        }
                        para = para + " - ";
                    }
                    
                    if(resultPair.containsKey("SNIP"))
                    {
                        if(para == null)
                        {
                            para = resultPair.get("SNIP");
                        }
                        else
                        {
                            para = para + "  " + resultPair.get("SNIP");
                        }
                    }
                    
                    if (resultPair.containsKey("HREF"))
                    {
                        resultLink = resultPair.get("HREF");
                    }
                    
    
                    if (resultPair.containsKey("QE"))
                    {
                        qe = resultPair.get("QE");
                    }
                    
                    
                    if (resultPair.containsKey("DYM"))
                    {
                        DYMWord = resultPair.get("DYM");
                    }
                    
                    if(para != null)
                    {
                        outputBuf.append("<p>"+para+"</p1>"+"<br>");
                    }
                    if(resultLink != null)
                    {
                        outputBuf.append("<a href="+filePath+">" + resultLink + "</a>");
                    }
                    
                    if(qe != null)
                    {
                        outputBuf.append("<p>"+" Consider adding these terms in your query -  "+qe+"</p1>");
                    }
                    
                    if(DYMWord != null)
                    {
                        outputBuf.append("<p>"+" Did You Mean -  "+DYMWord+" ? " +"</p1>");
                    }
                    
                }
                                
                
                out.println("results are about to come");
                outputBuf.append("</body>");
                outputBuf.append("</html>");

                request.setAttribute("result", outputBuf.toString());
                request.getRequestDispatcher("/ShowAll.jsp").forward(request, response);
                
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
        
            /**for(String docid : results)
            {
                out.println(docid);
            }**/
        }   
    }

    /**
     * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
     */
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // TODO Auto-generated method stub
    }

}

