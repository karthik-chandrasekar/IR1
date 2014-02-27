package edu.asu.irs13;
import org.apache.lucene.index.*;
import java.util.*;
import org.apache.lucene.store.*;
import java.io.File;
import java.util.Scanner;

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
        long startTime = System.currentTimeMillis();
        
        //Override the comparator to sort the relMap by value. 
        ValueComparator bvc =  new ValueComparator(relMap);
        TreeMap<String,Double> sortedMap = new TreeMap<String,Double>(bvc);
        sortedMap.putAll(relMap);
        
        //Print top ten high relevant elements
        int loopVar = 0;
        HashMap<String, Double> topTenSimilarDocs = new HashMap<String, Double>();
        for(Map.Entry<String, Double> pair : sortedMap.entrySet())
        {
            loopVar++;
            if(loopVar >10)
                break;
            topTenSimilarDocs.put(pair.getKey(), pair.getValue());
           System.out.println(pair.getKey() + "  " + pair.getValue());
        }
        sObj.computeAuthorityHub(topTenSimilarDocs);
        
        long endTime = System.currentTimeMillis();
        System.out.println("Time taken for sorting stuffs compute is "+ (double)(endTime - startTime)/1000);
    }
    
    public void computeAuthorityHub(Map<String, Double> rootSet) throws Exception
    {
        LinkAnalyses.numDocs = 25054;
        LinkAnalyses la = new LinkAnalyses();
        System.out.println("Declaring biggg matrix");
        
        int [][] authAdj = new int[25054][25054];
        int [][] hubAdj = new int[25054][25054];
        int []  authScores = new int[25054];
        int [] hubScores  = new int[25054];
        int [] prevAuthScores = new int[25054];
        int [] prevHubScores = new int[25054];
        int key, row, col, maxIter = 0;
        double conv = 0.0;
        
        System.out.println("Forming adjacency matrix");
        for(Map.Entry<String, Double> pair: rootSet.entrySet())
        {   
            //Forming adjacency matrix of hubs and authorities
            int links[] = la.getLinks(Integer.parseInt(pair.getKey()));
            int cits[] = la.getCitations(Integer.parseInt(pair.getKey()));
            key = Integer.parseInt(pair.getKey());
        
            for(int val: links)
            {
                authAdj[key][val] = 1; 
            }
            
            for(int val: cits)
            {
                hubAdj[key][val] = 1;
            }
        }
        
        System.out.println("Initializing authScores and hubScores");
        //Initialize all 1's to authScores and hubScores
        for(row=0;row<25054;row++)
        {
            authScores[row] = 1;
            hubScores[row] = 1;
        }
        
        System.out.println("Starting convergance iteration");
        //Iterate it  till its converges
        while(true)
        {
            //Single iteration to find authorities and hubs
            for(row=0; row < 25054; row++)
            {
                //Authority computation
                for(col=0;col<25054;col++)
                {
                    prevAuthScores[col] = authScores[col];
                    authScores[row] += authAdj[row][col] * hubScores[col];
                }
            
                //Hub computation
                for(col=0;col<25054;col++)
                {
                    prevHubScores[col] = hubScores[col];
                    hubScores[row] += hubAdj[row][col] * authScores[col];
                }
            }
            
            //Check for convergence
            for(row=0;row<25054;row++)
            {
                conv += Math.pow((prevAuthScores[row] - authScores[row]), 2); 
            }
            conv = Math.sqrt(conv);
            System.out.println(conv);
            if (conv<100 || maxIter > 1000)
            {   
                maxIter += 1;
                //if converged, break the iteration or if it reaches max iteration break it.
                System.out.println("System is converged");
                break;
            }
        }
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
    
    public static void main(String[] args) throws Exception
    {
        SearchFiles sObj = new SearchFiles();
        IndexReader r = IndexReader.open(FSDirectory.open(new File("index")));
        
        //Compute two norm for all the documents in the corpus
        sObj.getTwoNorm(r, sObj);
        
        Scanner sc = new Scanner(System.in);
        String str = "";
        System.out.print("query> ");
        
        while(!(str = sc.nextLine()).equals("quit"))
        {   
            long startTime = System.currentTimeMillis();
            
            //Tf ordering of results
            HashMap<String, Double> relMapTf = new HashMap<String, Double>();
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

