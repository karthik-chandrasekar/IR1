package edu.asu.irs13;
import org.apache.lucene.index.*;
import java.util.*;
import org.apache.lucene.store.*;
import org.apache.lucene.document.*;
import java.io.File;
import java.util.Scanner;
import java.util.Arrays;
import java.text.NumberFormat;

class ValueComparator implements Comparator<String> {

    Map<String, Double> base;
    public ValueComparator(Map<String, Double> base) {
        this.base = base;
    }

    // This comparator imposes orderings that are inconsistent with equals.    
    public int compare(String a, String b) {
        if (base.get(a) >= base.get(b)) {
            return -1;
        } else {
            return 1;
        } 
    }
}

public class SearchFiles {
    
    HashMap<Integer, Integer> twoNorm = new HashMap<Integer, Integer>();
    int docSize = 25054;
    double minIdf=100000;
    String minIdfTerm;
    HashMap<Integer, Double> docPageRankMap = new HashMap<Integer, Double>();
    double [] pageRankVector = new double[docSize]; 
  
    int maxPageRankIndex=0;
    int minPageRankIndex=0;
    
    double wProb = 0.4;
    double cProb = 0.8;
    int K = 10;
 
    public void getTwoNorm(IndexReader r, SearchFiles sObj) throws Exception
    {
        long startTime = System.currentTimeMillis();
        TermEnum t = r.terms();
        int freq;
        int totalDocs = r.maxDoc();
        double  Idf;
    
        while(t.next())
        {   
            Term te = new Term("contents", t.term().text());
            TermDocs td = r.termDocs(te);
            while(td.next())
            {
                freq = 0;
                if (twoNorm.containsKey(td.doc()))
                {
                    freq = twoNorm.get(td.doc());
                }
                freq += td.freq() * td.freq();
                twoNorm.put(td.doc(), freq);
            }
            //To find the term with least Idf value
            Idf = (double)(totalDocs/(double)r.docFreq(t.term()));
            //System.out.println(t.term().text());
            if (Idf <= sObj.minIdf)
            {
                sObj.minIdf = Idf;
                sObj.minIdfTerm = t.term().text();
                System.out.println(sObj.minIdfTerm);
            }
        }
        long endTime = System.currentTimeMillis();
        System.out.println("Time take for twoNorm compute is "+ (double)(endTime - startTime)/1000);
    }
    
    
    public void showResults(Map<String, Double> relMap, IndexReader r, SearchFiles sObj) throws Exception
    {
        
        //Override the comparator to sort the relMap by value. 
        ValueComparator bvc =  new ValueComparator(relMap);
        TreeMap<String,Double> sortedMap = new TreeMap<String,Double>(bvc);
        sortedMap.putAll(relMap);
        
        //Print top ten high relevant elements
        int loopVar = 0;
        HashMap<String, Double> topTenSimilarDocs = new HashMap<String, Double>();
        System.out.println("Ordereing results based on TfIdf");
        for(Map.Entry<String, Double> pair : sortedMap.entrySet())
        {
            loopVar++;
            if(loopVar >K)
                break;
            topTenSimilarDocs.put(pair.getKey(), pair.getValue());
            System.out.println(pair.getKey());
            Document d = r.document(Integer.parseInt(pair.getKey()));
            String url = d.getFieldable("path").stringValue();
            System.out.println("Url is "+ url.replace("%%", "/"));
        }
        long startTime = System.currentTimeMillis();
        sObj.computeAuthorityHub(topTenSimilarDocs, r);
        long endTime = System.currentTimeMillis();
        System.out.println("Time taken for Auth and Hubs "+ (double)(endTime - startTime)/1000);
        sObj.pageRankOrdering(relMap, r);
        
    }

    
    public void pageRankOrdering(Map<String, Double> relMap, IndexReader r) throws Exception
    {
        Map<String, Double> pageRankResults= new HashMap<String, Double>();
        
        double relMax = 0.0;
        double relMin = 100000.0;
      
        
        
        //Find Tf/Idf relavance max and min to normalize
        for(Map.Entry<String, Double> pair:relMap.entrySet())
        {
            if (relMax < pair.getValue())
            {
                relMax = pair.getValue();
            }
            if(relMin> pair.getValue())
            {
                relMin = pair.getValue();
            }
        }
        
       
        double temp;
        double finalTemp;
        
        //Relevance value normalization to zero to one
        System.out.println("Using W probablity" + wProb);
        for(Map.Entry<String, Double> pair:relMap.entrySet())
        {
            
            temp = 0.0;
            finalTemp = 0.0;
            temp = ((pair.getValue() - relMin) / (relMax - relMin));
            finalTemp = wProb *  pageRankVector[Integer.parseInt(pair.getKey())] + (1-wProb) * temp; 
            pageRankResults.put(pair.getKey(), finalTemp);
        }
        
     
         ValueComparator bvc =  new ValueComparator(pageRankResults);
         TreeMap<String,Double> sortedMap = new TreeMap<String,Double>(bvc);
         sortedMap.putAll(pageRankResults);
         
         //Printing page rank ordered results
         System.out.println("Page Rank Ordered Results");
         
         int loopLimit =0;
         for(Map.Entry<String, Double> pair:sortedMap.entrySet())
         {
             loopLimit ++;
             System.out.println(pair.getKey());
             Document d = r.document(Integer.parseInt(pair.getKey()));
             String url = d.getFieldable("path").stringValue();
             //System.out.println("Url is "+ url.replace("%%", "/"));
             if (loopLimit == 20)
                 break;
         }
    }
    
    public void computeAuthorityHub(Map<String, Double> rootSet, IndexReader r) throws Exception
    {
        LinkAnalyses.numDocs = 25054;
        LinkAnalyses la = new LinkAnalyses();

        System.out.println("Inside compute Authority Hub");
        //Form base set - collect both links and citations of root set docs 
        if (rootSet.isEmpty()) return;
        HashSet<Integer> baseSet = new HashSet<Integer>();       
        for(Map.Entry<String, Double> pair:rootSet.entrySet())
        {
            int docNum = Integer.parseInt(pair.getKey());
            int links[] = la.getLinks(docNum);
            int citations[] = la.getCitations(docNum);
            baseSet.add(docNum);         
            for(int link:links)
            {
                baseSet.add(link);
            }
            
            for(int cite:citations)
            {
                baseSet.add(cite);
            }
        }
        
        //Creating alias for original doc numbers 
        HashMap<Integer, Integer> origDocToAliasDocMap = new HashMap<Integer, Integer>();
        HashMap<Integer, Integer> aliasDocToOrigDocMap = new HashMap<Integer, Integer>();
        int docCount = baseSet.size();
        System.out.println("Base set" + docCount);
        int count = 0;

        for(Integer docNum:baseSet)
        {
            origDocToAliasDocMap.put(docNum, count);
            aliasDocToOrigDocMap.put(count, docNum);
            count ++;
        }
        
       // System.out.println("Before adjacency matrix construction");
        //System.out.println("Base set length" + baseSet.size());

        
        //Adjacency matrix construction
        int [][] adjMatrix = new int[docCount][docCount];
        int [][] adjMatrixTrans = new int[docCount][docCount];
        int [][] authMatrix = new int[docCount][docCount];
        int [][] hubMatrix = new int[docCount][docCount];
        
        double [] authVector = new double[docCount];
        double [] authTempVector = new double[docCount];
        double [] hubVector = new double[docCount];
        double [] hubTempVector = new double[docCount];       
 
        //Initialize the authVector to 1
        long startTime = System.currentTimeMillis();
        
        Arrays.fill(authVector, 1.0);
        Arrays.fill(hubVector, 1.0);
        

        for(Integer docNum:baseSet)
        {
            int links[] = la.getLinks(docNum);
            for(int link:links)
            {
                if (baseSet.contains(link) && baseSet.contains(docNum))
                {
                    //Getting alias doc number and using it in adj matrix
                    docNum = origDocToAliasDocMap.get(docNum);
                    link = origDocToAliasDocMap.get(link);
                    adjMatrix[docNum][link] = 1;
                }
            }
        }
        
        //Matrix Transpose
        for(int i=0;i<docCount; i++)
        {
            for(int j=0; j<docCount; j++)
            {
                adjMatrixTrans[i][j] = adjMatrix[j][i];
            }
        }
        
        int temp = 0;
        //Finding authMatrix ==> adjMatrixTrans * adjMatrix
        
        for(int i=0; i<docCount; i++)
        {
            for(int j=0; j<docCount; j++)
            {
                for(int k=0; k<docCount; k++)
                {
                    temp += adjMatrixTrans[i][k] * adjMatrix[k][j];
                }
                authMatrix[i][j] = temp;
                temp = 0;
            }
        }
        
        //System.out.println("Finding hubMatrix");
        //Finding hubMatrix ==> adjMatrix * adjMatrixTrans
        for(int i=0; i<docCount; i++)
        {
            for(int j=0; j<docCount; j++)
            {
                for(int k=0; k<docCount; k++)
                {
                    temp += adjMatrix[i][k] * adjMatrixTrans[k][j];
                }
                hubMatrix[i][j] = temp;
                temp = 0;
            }
        }
        
        long endTime = System.currentTimeMillis();
        System.out.println("Time taken to form based set and adj matrix "+ (double)(endTime - startTime)/1000);

        
        int converge = 0;
        //Do power iteration  for authority computation till it converges
        
       // System.out.println("Before convergence");

        startTime = System.currentTimeMillis();

        int authIterationCount = 0;
        while(converge==0)
        {
            authIterationCount ++;
            //System.out.println("Inside power iteration");
            
            //authMatrix authVector multiplication
            for(int i=0; i<docCount; i++)
            {
                for(int j=0; j<docCount; j++)
                {
                    authTempVector[i] += authMatrix[i][j] * authVector[j];
                }
            }
  
           // System.out.println("Printing authVector -----------------------------------------------------------------");
           
            // finding unit authVector
            double unitSum = 0;
            for(int i=0; i< docCount; i++)
            {
                unitSum += authTempVector[i]*authTempVector[i];
            }
            unitSum  = Math.sqrt(unitSum);
            
            for(int i=0; i< docCount; i++)
            {
                authTempVector[i] = (authTempVector[i] / unitSum);
            }
            double threshold = 0.001;
            double diff;

          //checking for convergence
            for(int i=0; i<docCount; i++)
            {
                diff = Math.abs(authTempVector[i] - authVector[i]);
                
                if (diff <= threshold || diff == 0)
                {
                    converge = 1;
                }
                else
                {
                    //System.out.println("Difference "+ diff + "index " + i);
                    converge = 0;
                    break;
                }
            }
            
            for(int i=0; i<docCount; i++)
            {
                authVector[i] = authTempVector[i];
            }
    
        }
        endTime = System.currentTimeMillis();
        System.out.println("Time taken to converge Authority "+ (double)(endTime - startTime)/1000);
        System.out.println("Number of Authoriy Convergence Iteration " + authIterationCount);
        
        startTime = System.currentTimeMillis();
        converge = 0;
        //Do power iteration  for hub computation till it converges
        int hubIterationCount = 0;
        while(converge==0)
        {
            hubIterationCount++;
            //System.out.println("Inside  hub power iteration");
            
            //hubMatrix hubVector multiplication
            for(int i=0; i<docCount; i++)
            {
                for(int j=0; j<docCount; j++)
                {
                    hubTempVector[i] += hubMatrix[i][j] * hubVector[j];
                }
            }
  
           //System.out.println("Printing hubVector -----------------------------------------------------------------");
           
            // finding unit hubVector
            double unitSum = 0;
            for(int i=0; i< docCount; i++)
            {
                unitSum += hubTempVector[i] * hubTempVector[i];
            }
            unitSum  = Math.sqrt(unitSum);
            
            for(int i=0; i< docCount; i++)
            {
                hubTempVector[i] = (hubTempVector[i] / unitSum);
            }
            double threshold = 0.001;
            double diff;

          //checking for convergence
            for(int i=0; i<docCount; i++)
            {
                diff = Math.abs(hubTempVector[i] - hubVector[i]);
                //System.out.println("Difference "+ diff + "index " + i);
                if (diff <= threshold || diff == 0)
                {
                    converge = 1;
                }
                else
                {
                    converge = 0;
                    break;
                }
            }
            
            for(int i=0; i<docCount; i++)
            {
                hubVector[i] = hubTempVector[i];
            }
        }  
        endTime = System.currentTimeMillis();
        System.out.println("Time taken to converge Hub "+ (double)(endTime - startTime)/1000);
        System.out.println("Number of Hub Convergence Iteration " + hubIterationCount);

        
        startTime = System.currentTimeMillis();
        // Print top ten authority resutls
        Map<String, Double> AuthResultMap = new HashMap<String, Double>();
        for(int i=0; i<docCount; i++)
        {
            //System.out.print(" "+ authVector[i]);
            AuthResultMap.put(Integer.toString(aliasDocToOrigDocMap.get(i)), authVector[i]);
        }
        ValueComparator bvc =  new ValueComparator(AuthResultMap);
        TreeMap<String,Double> authSortedMap = new TreeMap<String,Double>(bvc);
        authSortedMap.putAll(AuthResultMap);
        System.out.println("Top ten Authorities");
        
        int authCount = 0;
        for(Map.Entry<String, Double> pair:authSortedMap.entrySet())
        {
            if (authCount == 20)break;
            System.out.println(pair.getKey());
            Document d = r.document(Integer.parseInt(pair.getKey()));
            String url = d.getFieldable("path").stringValue();
            //System.out.println("Url is "+ url.replace("%%", "/"));
            authCount++;
        }
        endTime = System.currentTimeMillis();
        System.out.println("Time taken to find top ten authority "+ (double)(endTime - startTime)/1000);

        
        startTime = System.currentTimeMillis();
        //Print top ten hub results
        Map<String, Double> HubResultMap = new HashMap<String, Double>();
        for(int i=0; i<docCount; i++)
        {
            //System.out.print(" "+hubVector[i]);
            HubResultMap.put(Integer.toString(aliasDocToOrigDocMap.get(i)), hubVector[i]);
        }
        ValueComparator hubComp =  new ValueComparator(HubResultMap);
        TreeMap<String,Double> hubSortedMap = new TreeMap<String,Double>(hubComp);
        hubSortedMap.putAll(HubResultMap);
        System.out.println("Top ten Hubs");
        
        int hubCount = 0;
        for(Map.Entry<String, Double> pair:hubSortedMap.entrySet())
        {
            if (hubCount == 20)break;
            System.out.println(pair.getKey());
            Document d = r.document(Integer.parseInt(pair.getKey()));
            String url = d.getFieldable("path").stringValue();
            //System.out.println("Url is "+ url.replace("%%", "/"));
            hubCount++;
        }
        endTime = System.currentTimeMillis();
        System.out.println("Time taken to find top ten hubs "+ (double)(endTime - startTime)/1000);

    }
 
    public void orderUsingTf(String str, IndexReader r, SearchFiles sObj, Map<String, Double> relMapTf) throws Exception
    {
        String[] terms = str.split("\\s+");
        int queryLen = terms.length;
        double relTf;
        long startTime = System.currentTimeMillis();
        for(String word : terms)
        {
            Term term = new Term("contents", word);
            TermDocs tdocs = r.termDocs(term);
            while(tdocs.next())
            {
                //To restrict the document size
                if (tdocs.doc() > sObj.docSize)
                {
                    continue;
                }
                relTf = 0;                  
            
                if(relMapTf.containsKey(tdocs.doc()))
                {
                    relTf = relMapTf.get(tdocs.doc());
                }       
                relTf += (double)(tdocs.freq())/(Math.sqrt(queryLen) * Math.sqrt(sObj.twoNorm.get(tdocs.doc())));
                relMapTf.put(Integer.toString(tdocs.doc()), relTf);
            }
        }
        System.out.println("Total number of results of Tf " + relMapTf.size());
        //sObj.showResults(relMapTf, r, sObj);
        long endTime = System.currentTimeMillis();
        System.out.println("Ordering based on Tf results -Time Taken "+ (double)(endTime - startTime)/1000);
    }
    
    public void orderUsingTfIdf(String str, IndexReader r, SearchFiles sObj, Map<String, Double> relMapTfIdf) throws Exception
    {
        String[] terms = str.split("\\s+");
        int queryLen = terms.length;
        double relTfIdf;
        long startTime = System.currentTimeMillis();
        double Idf;
        int totalDocs = r.maxDoc();
        for(String word : terms)
        {
            Term term = new Term("contents", word);
            TermDocs tdocs = r.termDocs(term);
            while(tdocs.next())
            {
                if (tdocs.doc() > sObj.docSize)
                {
                    continue;
                }
                relTfIdf = 0;                   
                if(relMapTfIdf.containsKey(tdocs.doc()))
                {
                    relTfIdf = relMapTfIdf.get(tdocs.doc());
                }
                
                Idf = (double)(totalDocs/(double)r.docFreq(term));
                Idf = Math.log(Idf)/Math.log(2);
                //System.out.println("Idf  " + Idf);
                relTfIdf += (double)((tdocs.freq() * Idf )/((Math.sqrt(queryLen) * Math.sqrt(sObj.twoNorm.get(tdocs.doc())))));
                relMapTfIdf.put(Integer.toString(tdocs.doc()), relTfIdf);       
            }
        }
        System.out.println(" Total number of results of TfIdf " + relMapTfIdf.size());
        sObj.showResults(relMapTfIdf, r, sObj);
        long endTime = System.currentTimeMillis();
        System.out.println("Ordering based on TfIdf results -Time Taken "+ (double)(endTime - startTime)/1000);
    }
   
    public void getMemoryUsage()
    {
        Runtime runtime = Runtime.getRuntime();

        NumberFormat format = NumberFormat.getInstance();

        StringBuilder sb = new StringBuilder();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();

        sb.append("Total Memory: " + format.format(totalMemory / 1024) + "<br/>");
        sb.append("Free memory: " + format.format(freeMemory / 1024) + "<br/>");
        System.out.println(sb);
    }
    
    public void computePageRank(SearchFiles sObj, IndexReader r) throws Exception
    {
        LinkAnalyses.numDocs = 25054;
        LinkAnalyses la = new LinkAnalyses();

        int docCount = 25054;
        int nonZeroCount = 0;
        double sinkValue = (double)1/docCount;
        double kValue = (double)(1-cProb) * sinkValue;
        double temp = 0;
      

        double [] tempPageRankVector = new double[docCount];
        double [] markovMatrix = new double[docCount];
        int [] row = new int[docCount];
        int [] tempRow = new int[docCount];
        int [] column = new int[docCount];

        Map<Integer, Integer> nonZeroCountHash = new HashMap<Integer, Integer>();
        getMemoryUsage();

        //Setting initial page rank vector values to be 1
        Arrays.fill(pageRankVector, sinkValue);

        //Fill the hash with non zero count
        for(int i=0; i< docCount; i++)
        {
            nonZeroCount = 0;
            column = la.getLinks(i);
            
            if (column != null)
            {
                nonZeroCount = column.length;
            }
            column = new int[docCount];
            nonZeroCountHash.put(i, nonZeroCount);
        }
 
        
        int converge = 0;
        int convergeCount = 0;
        
        while(converge == 0)
        {
            convergeCount++;
            //maxIteration++;
            /*if(maxIteration == 2)
            {
                break;
            }*/
            
            //Single matrix * vector multiplication
            System.out.println("Trying to converge");
            for(int i=0; i < docCount; i++)
            {
                /*if (i ==10)
                {
                    break;
                }*/
                tempRow = la.getCitations(i);
                Arrays.fill(row, 0);
                
                //populate the row with non zero values
                if (tempRow != null)
                {
                    for(int m=0; m<tempRow.length; m++)
                    {
                        row[tempRow[m]] = 1;
                    }
                }
                tempRow = new int[docCount];
                
                for(int j=0; j < docCount; j++)
                {
                    nonZeroCount = nonZeroCountHash.get(j);

                    if (nonZeroCount ==0)
                    {
                        markovMatrix[j] = (cProb * sinkValue) + kValue;
                    }   
                    else
                    {
                        markovMatrix[j] = cProb * ((double)row[j]/nonZeroCount) + kValue;
                    }
                } 

                //row * page rank vector 
                for(int l=0; l<docCount; l++)
                {
                   temp += markovMatrix[l]*pageRankVector[l]; 
                }
                tempPageRankVector[i] = temp;
                 
                //System.out.println("Index " + i + " value " + temp);
                temp = 0; 
            }


            double threshold = 0.001;
            double diff;

            //Checking for convergence
            for(int i=0; i<docCount; i++)
            {
                diff = Math.abs(tempPageRankVector[i] - pageRankVector[i]);
                
                if(diff <= threshold || diff == 0)
                {
                    converge = 1;
                }
                else
                {
                    System.out.println("Diff "+ diff + "Index " + i);
                    converge = 0;
                    break;
                }
            }
            
            for(int i=0; i<docCount; i++)
            {
                pageRankVector[i] = tempPageRankVector[i];
            }
         
        }
        
        System.out.println("Total number of iterations taken for page rank convergence - " + convergeCount);
        
        sObj.normalizePageRank(r);
        
    }
    
    
    public void normalizePageRank(IndexReader r) throws Exception
    {
        //Normalize page rank to the limits [0,1]
        
        double temp;
        double pageRankMax = 0.0;
        double pageRankMin = 100000.0;
        int maxPageRankIndex = 0;
        int minPageRankIndex = 0;
   
        for(int i=0; i<docSize; i++)
        {
            if (pageRankMax < pageRankVector[i])
            {
                pageRankMax = pageRankVector[i];
                maxPageRankIndex = i;
            }
            
            if (pageRankMin > pageRankVector[i])
            {
                pageRankMin = pageRankVector[i];
                minPageRankIndex = i;
            }
            
        }

        
        System.out.println("Page Rank before normalizing");
        System.out.println("Page Rank Max is " + pageRankMax + "Doc id  - " + maxPageRankIndex);
        System.out.println("Page Rank Min is " + pageRankMin + "Doc id - " + minPageRankIndex );
        

        
        for(int i=0; i<docSize; i++)
        {
            temp = 0.0;
            temp = (double)(pageRankVector[i] - pageRankMin) / (pageRankMax - pageRankMin);
            pageRankVector[i] = temp;
        }

        pageRankMax = 0.0;
        pageRankMin = 100000.0;
        
        for(int i=0; i<docSize; i++)
        {
            if (pageRankMax < pageRankVector[i])
            {
                pageRankMax = pageRankVector[i];
            }
            
            if (pageRankMin > pageRankVector[i])
            {
                pageRankMin = pageRankVector[i];
            }
            
        }
        
        System.out.println("Page Rank after normalizing");
        System.out.println("Page Rank Max is " + pageRankMax);
        System.out.println("Page Rank Min is " + pageRankMin);

        Document d = r.document(maxPageRankIndex);
        String url = d.getFieldable("path").stringValue();
        System.out.println("Url is "+ url.replace("%%", "/"));
        
    }

 
    public static void main(String[] args) throws Exception
    {
        SearchFiles sObj = new SearchFiles();
        IndexReader r = IndexReader.open(FSDirectory.open(new File("index")));
        
        //Compute two norm for all the documents in the corpus
        sObj.getTwoNorm(r, sObj);
        
        //PageRank computation
        sObj.computePageRank(sObj, r);
        sObj.getMemoryUsage();
        System.out.print("Page Rank Computation is overerrrrrr !!!!!");
        
        Scanner sc = new Scanner(System.in);
        String str = "";
        System.out.print("query> ");
        String [] parts;
        
        
        while(!(str = sc.nextLine()).equals("quit"))
        {   
            long startTime = System.currentTimeMillis();
            str = str.toLowerCase();
           
            //To handle W Threshold value in query time 
            if (str.startsWith("WThreshold"))
            {
                parts = str.split("=");
                sObj.wProb = Double.valueOf(parts[1]);
                System.out.print("query> ");
                continue;
            }
                
            //Tf ordering of results
            //sObj.orderUsingTf(str, r, sObj, relMapTf);
                    
            // TfIdf ordering of results
            HashMap<String, Double> relMapTfIdf = new HashMap<String, Double>();        
            sObj.orderUsingTfIdf(str, r, sObj, relMapTfIdf);
            
            long endTime = System.currentTimeMillis();
            
            //System.out.println("Least Idf value is " + sObj.minIdf + " Term is " + sObj.minIdfTerm);
            System.out.println("Time taken to get results "+ (double)(endTime - startTime)/1000);
            System.out.print("query> ");
        }
        sc.close();
    }   
} 

