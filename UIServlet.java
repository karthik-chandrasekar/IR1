package KarthikProject3Servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

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
        String Query = request.getParameter("Query");
        String method = "";
        StringBuilder outputBuf = new StringBuilder();
        String localFilePath = "/Users/karthikchandrasekar/Downloads/temp/result3";
        String filePath = "";
        
        List<String> results = new ArrayList<String>();
        
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
                
                out.println("Query " + Query + " Method " + method);
                results = sObj.servletCall(Query, method);
                
                out.println("Size of returned list is" + results.size());
                
                outputBuf.append("<html>");
                outputBuf.append("<body>");

                for(String result : results)
                {
                    out.println(result);
                    filePath = localFilePath + result;
                    outputBuf.append("<a href="+filePath+">" + result + "</a>" + "<br>");
                    //outputBuf.append("<p>"+result+"</p1>");
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

