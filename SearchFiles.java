package edu.asu.irs13;

import org.apache.lucene.index.*;
import java.util.*;
import org.apache.lucene.store.*;
import org.apache.lucene.document.*;
import org.jsoup.Jsoup;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Arrays;
import java.util.Set;
import java.text.NumberFormat;
import java.util.Collections;

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


class ResultsComparator implements Comparator<Integer> {

    Map<Integer, Integer> base;
    public ResultsComparator(Map<Integer, Integer> base) {
        this.base = base;
    }

    // This comparator imposes orderings that are inconsistent with equals.    
    public int compare(Integer a, Integer b) {
        if (base.get(a) >= base.get(b)) {
            return -1;
        } else {
            return 1;
        } 
    }
}


class KeyWordsComparator implements Comparator<String> {

    Map<String, Float> base;
    public KeyWordsComparator(Map<String, Float> base) {
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
    
    int docSize = 25054;
    
    IndexReader r;
    
    
    HashMap<Integer, Integer> twoNorm = new HashMap<Integer, Integer>();
    HashMap<Integer, Double> twoNormTfIdf = new HashMap<Integer, Double>();
    HashMap<Integer, Double> docPageRankMap = new HashMap<Integer, Double>();
    List<String> termList = new ArrayList<String>();
    
    //Results
    List<String> TfIdfResults = new ArrayList<String>();
    List<String> PageRankResults = new ArrayList<String>();
    List<String> AHResults = new ArrayList<String>();
    List<String> tfIdfClusterResults = new ArrayList<String>();
    
    Map<String, String> UIResults = new HashMap<String, String>();
    List<Map<String, String>> UIResultsList = new ArrayList<Map<String, String>>();
    
    float[][] corMatrix ;        

    
    //Clustering

    //Data structure to hold words of each document along with its TfIdf values
    //Key - Doc id, Value - Map(Word, TfIdf)
    Map<Integer, Map<String, Float>> docWordsMap = new HashMap<Integer, Map<String, Float>>();
    Map<Integer, Map<String, Integer>> docWordsTfMap = new HashMap<Integer, Map<String, Integer>>();
    
    int kSize = 3;
    int rCount = 3;
       
    //String indexPath = "/Users/karthikchandrasekar/Desktop/SecondSem/IR/Project1/irs13/index";
    String indexPath = "/Users/karthikchandrasekar/Desktop/SecondSem/IR/IR-NewIndex/IR-IndexedFiles/result3/cleanedHtmlFiles/index";
    
    String htmlFilePath = "/Users/karthikchandrasekar/Desktop/SecondSem/IR/Project1/irs13/result3/";
    
    double [] pageRankVector = new double[docSize]; 
  
    String minIdfTerm;
    String inputQuery="";
    
    int maxPageRankIndex=0;
    int minPageRankIndex=0;
    double pageRankThreshold = 0.00001;
    
    double wProb = 0.4;
    double cProb = 0.4;
    int resultsCount = 50;
 
    //Query elaboration flag
    int queryElborate = 0;
    
    
    public void getTwoNorm(IndexReader r, SearchFiles sObj) throws Exception
    {
        
        //Find two norm values for Tf and TfIdf
        
        long startTime = System.currentTimeMillis();
        
        TermEnum t = r.terms();
        int freq;
        int totalDocs = r.maxDoc();
        double  Idf;
        double IdfTemp;
        Map<String, Float> wordMap;
        Map<String, Integer> wordTfMap;
        int docNum;
        String docTerm;

    
        while(t.next())
        {   
            docTerm = t.term().text();
            Term te = new Term("contents", docTerm);
            TermDocs td = r.termDocs(te);
            
            while(td.next())
            {
                //Two norm values of docs based on Tf values
                freq = 0;
                docNum = td.doc();
                if (twoNorm.containsKey(docNum))
                {
                    freq = twoNorm.get(docNum);
                }
                freq += td.freq() * td.freq();
                twoNorm.put(docNum, freq);
                
               //Two norm values of docs based on TfIdf values 
               IdfTemp = 0.0;
               if (twoNormTfIdf.containsKey(docNum))
               {
                   IdfTemp = twoNormTfIdf.get(docNum);
               }
               Idf = (double)(totalDocs/(double)r.docFreq(t.term()));
               Idf = td.freq() * (Math.log(Idf)/Math.log(2));
               IdfTemp += Idf * Idf;
               twoNormTfIdf.put(docNum, IdfTemp);
               
                          
               //Populating docWordsMap to be used for clustering
               //Map{doc_id, Map{term, TfIdf}} - Data structure of docWordsMap
               if (sObj.docWordsMap.containsKey(docNum))
               {
                   wordMap = sObj.docWordsMap.get(docNum);
                   wordMap.put(docTerm, (float)Idf);
                   sObj.docWordsMap.put(docNum, wordMap);   
               }
               else
               {
                   wordMap = new HashMap<String, Float>();
                   wordMap.put(docTerm, (float)Idf);
                   sObj.docWordsMap.put(docNum, wordMap);
               }   
              
            }
            termList.add(docTerm);
        }
        
        long endTime = System.currentTimeMillis();
        System.out.println("Time take for twoNorm compute is "+ (double)(endTime - startTime)/1000);
        
    }
   
    public void showResults(Map<String, Double> relMap, IndexReader r, SearchFiles sObj) throws Exception
    {
        
        sObj.TfIdfResults.clear();
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
            if(loopVar > resultsCount)
                break;
            topTenSimilarDocs.put(pair.getKey(), pair.getValue());
            
            //Get url of the doc id
            
            sObj.TfIdfResults.add(pair.getKey());
        }
        long startTime = System.currentTimeMillis();
        //sObj.computeAuthorityHub(topTenSimilarDocs, r, sObj);
        long endTime = System.currentTimeMillis();
        System.out.println("Time taken for Auth and Hubs "+ (double)(endTime - startTime)/1000);
        //sObj.pageRankOrdering(relMap, r, sObj);
        
        //Find the most functional synonym of the entered words and return it
        // Call this only when queryElaborate flag is set
        if (sObj.queryElborate == 1)
        {   
            scalarAssociation(sObj);
        }
        else if (loopVar > 2)
        {
            sObj.resultsClustering(sObj, loopVar);
        }
    }
    
    
    void scalarAssociation(SearchFiles sObj)
    {
        
        //Step 1 - Get a set of all words
        System.out.println("Step 1 - Get a set of all words");
        Set<String> allWordsSet = new HashSet<String>();
        int docNum;
        for(String docId : TfIdfResults)
        {
            docNum = Integer.parseInt(docId);
            for(Map.Entry<String, Float> pair : docWordsMap.get(docNum).entrySet())
            {
                allWordsSet.add(pair.getKey());
            }
        }
        
            
      //Populating docWordsTfMap to be used for scalar association clustering
       //Map{doc_id, Map{term, Tf}} - Data structure of docWordsTfMap
       Map<String, Integer> wordTfMap = new HashMap<String, Integer>();
        
       try
       {
           for(String word : allWordsSet)
           {
               Term term = new Term("contents", word);
               TermDocs td = r.termDocs(term);
               while(td.next())
               {
           
                   if(docWordsTfMap.containsKey(td.doc()))
                   {
                       wordTfMap = docWordsTfMap.get(td.doc());
                       wordTfMap.put(word, td.freq());
                       docWordsTfMap.put(td.doc(), wordTfMap);
                   }
                   else
                   {
                       wordTfMap = new HashMap<String, Integer>();
                       wordTfMap.put(word, td.doc());
                       docWordsTfMap.put(td.doc(), wordTfMap);
                   }
               }   
           }  
       }
       catch(Exception e)
       {
           
       }
            
        //Step 2 - Fill the doc term matrix
        System.out.println("Step 2 - Fill the doc term matrix");

        int wordsSize = allWordsSet.size();
        List<String> allWordList = new ArrayList<String>();
        int[][] docTermMatrix = new int[resultsCount][wordsSize];
        int rowIndex=0;
        allWordList.addAll(allWordsSet);
        
        System.out.println(TfIdfResults);
        
        for(String docId : TfIdfResults)
        {
            if (rowIndex == resultsCount)break;
            docNum = Integer.parseInt(docId);
            for(Map.Entry<String, Integer> pair : docWordsTfMap.get(docNum).entrySet())
            {
                docTermMatrix[rowIndex][allWordList.indexOf(pair.getKey())] = (docWordsTfMap.get(docNum).get(pair.getKey())).intValue();            
            }
            rowIndex++;
        }
        
        allWordsSet = null;
   
        
        //Step 4 - Find the dt' * dt matrix
        System.out.println("Step 4 - Find the dt' * dt matrix");

        corMatrix = new float[wordsSize][wordsSize];
        for(int i=0; i<wordsSize; i++)
        {
            for(int j=0; j<wordsSize; j++)
            {
                for(int k=0; k < resultsCount; k++)
                {
                    corMatrix[j][i] += docTermMatrix[k][i] * docTermMatrix[k][j];
                }
            }
        }
               
        
        //Just print corMatrix
        
        /**for(int i=0;i<wordsSize; i++)
        {
            for(int j=0; j<wordsSize; j++)
            {
                System.out.println("Matrix" + corMatrix[i][j]);
            }
        }**/
        
        //Freeing memory
        docTermMatrix = null;
        
        //Step 5 - Normalize correlation matrix
        System.out.println("Step 5 - Normalize correlation matrix");

        float[][] normCorMatrix = new float[wordsSize][wordsSize];        

         for(int i=0;i<wordsSize; i++)
         {
             for(int j=0; j<wordsSize; j++)
             {
                 //System.out.println("After nulling" + corMatrix[i][j]);

                 normCorMatrix[i][j] = (float)corMatrix[i][j]/(float)(corMatrix[i][i]+corMatrix[j][j]-corMatrix[i][j]);
                //System.out.println("Num" + corMatrix[i][j]);
                //System.out.println("Deno" + (corMatrix[i][i]+corMatrix[j][j]-corMatrix[i][j]));
                 //System.out.println("Norm Matrix" + normCorMatrix[i][j]);
             }
         }
        
         //Freeing memory
         corMatrix = null;
         
         System.out.println("Step 6 - Skipping scalar matrix");
         
         
        /***         
        //Step 6 - Find scalar matrix
        System.out.println("Step 6 - Find scalar matrix");

        float[][] scalarMatrix = new float[wordsSize][wordsSize];    
        Map<String, Float> firstTermMap = new HashMap<String, Float>();
        Map<String, Float> secondTermMap = new HashMap<String, Float>();
        System.out.println("WordSize " + wordsSize);
        System.out.println("Size of matrix is " + wordsSize);

        for(int i=0;i<wordsSize; i++)
        {
            //System.out.println("Value of i " + i);
            for(int j=0; j<wordsSize; j++)
            {
                //System.out.println("Value of j " + j);
                //Fill the first vector using its terms in map 1
                for(int k =0; k<wordsSize; k++)
                {

                    if (normCorMatrix[i][k] > 0)
                    {
                        firstTermMap.put(allWordList.get(k), normCorMatrix[i][k]);
                    }               
                }
                
                //Fill the second vector using its terms in map 2
                for(int k =0; k<wordsSize; k++)
                {
                    if (normCorMatrix[j][k] > 0)
                    {
                        secondTermMap.put(allWordList.get(k), normCorMatrix[j][k]);
                    }               
                }
                
                //Find vector similarity between ith and jth vectors    
                scalarMatrix[i][j] = scalarVectorSimilarity(firstTermMap, secondTermMap);
                firstTermMap.clear();
                secondTermMap.clear();
            }
        }
                
                    ***/
                    
        //Step 7 - Find the closest words to the entered query words and suggest them
        System.out.println("Step 7 - Find the closest words to the entered query words and suggest them");

        
        int iIndex=0;
        float cur = 0, max = 0, secondMax=0;
        int maxIndex=0, secondMaxIndex = 0;
        List<String> assocWordsList = new ArrayList<String>();
        
        for(String word : inputQuery.split(" "))
        {
            word = word.toLowerCase().trim();
            System.out.println(word);
            max = 0;
            secondMax = 0;
            if(allWordList.contains(word))
            {
                iIndex = allWordList.indexOf(word);
                System.out.println("iIndex " + iIndex);

                for(int k=0;k<wordsSize;k++)
                {
                    //cur = scalarMatrix[iIndex][k];
                    cur = normCorMatrix[iIndex][k];
                    
                    //System.out.println("Cur " + cur + " iIndex " + iIndex);
                    //System.out.println("Max " + max);
                    
                    if(cur>max)// We shud consider second max similar as first will be same word
                    {
                        secondMax = max;
                        secondMaxIndex = maxIndex;
                        max = cur;
                        maxIndex = k;
                    }
                    else if (cur>secondMax)
                    {
                        secondMax = cur;
                        secondMaxIndex = k;
                    }                   
                
                }
                assocWordsList.add(allWordList.get(secondMaxIndex));
            }
            else 
            {
                System.out.println("Search term is not present in document !!!! Pls check ");
            }
            System.out.println("Max value " + max);
        }
        System.out.println("Query eloboration suggestions");
        System.out.println(assocWordsList);   
        
        String assocWordString = null;
        for(String assocWord : assocWordsList)
        {
            if(assocWordString == null)
            {
                assocWordString = assocWord;
            }
            else
            {
                assocWordString = assocWordString + " , " +assocWord;
            }           
        }       
        
        sObj.UIResults.put("QE", assocWordString);
        sObj.UIResultsList.add(UIResults);
        //sObj.tfIdfClusterResults.add("Consider adding these words" +assocWordsList);
    }
    
    void displayClusters(Map<Integer, Integer> docClusterMap) throws Exception
    {   
        //1.Collect snippets for documents and clusters. 
        //2.Group cluster documents and display it. 
        
        ResultsComparator bvc =  new ResultsComparator(docClusterMap);
        TreeMap<Integer, Integer> sortedMap = new TreeMap<Integer, Integer>(bvc);
        sortedMap.putAll(docClusterMap);
        Map<Integer, Set<String>> clusterKeyWords = new HashMap<Integer, Set<String>>();
        Set<String> tempSet;
        int wordcount = 0;
        int docCount =0, skipFlag=0;
        Map<Integer, Integer> docPrintCountMap  = new HashMap<Integer, Integer>(); 
        String tempStr="";

        //Collect high TfIdf value words
        Map<Integer, Set<String>> docKeyWordsMap = new HashMap<Integer, Set<String>>();
        Map<Integer, List<String>> docHtmlWordsMap = new HashMap<Integer, List<String>>();
        
        
        //Temp data structures to collect the output of functions
        List<String> neighBourWordsList;
        Set<String> keyWordsSet;
        
        for(Map.Entry<Integer, Integer> pair: sortedMap.entrySet())
        {
            if(docPrintCountMap.containsKey(pair.getValue()))
            {
                if (docPrintCountMap.get(pair.getValue())<3)
                {
                    docCount ++;
                }
                else
                {
                    skipFlag = 1;
                }               
            }
            else
            {
                docCount = 1;
                skipFlag = 0;
            }
            
            docPrintCountMap.put(pair.getValue(), docCount);
            
            Document d = r.document(pair.getKey());
            String url = d.getFieldable("path").stringValue(); 
            url = url.substring(1, url.length());
            System.out.println("Url " + url);
            
            //Add key words of every document 
            keyWordsSet = getKeyWords(pair.getKey());
            docKeyWordsMap.put(pair.getKey(), keyWordsSet);
            
            // Add neighbour words of query in every document
            neighBourWordsList = getNeighbourWords(pair.getKey(), url);
            if (neighBourWordsList.isEmpty())
            {
                neighBourWordsList.addAll(keyWordsSet);
            }
            docHtmlWordsMap.put(pair.getKey(), neighBourWordsList);
            
                     
            if(skipFlag == 0)
            {
                
                System.out.println(pair.getValue()+" "+pair.getKey() + " - " + url.replace("%%", "/"));
                //System.out.println(docKeyWordsMap.get(pair.getKey()));
                
                //tfIdfClusterResults.add(pair.getValue()+" "+pair.getKey() + " - " + url.replace("%%", "/"));
                
                tempStr = null;
                int snipCount =0;
                for(String snip : docHtmlWordsMap.get(pair.getKey()))
                {
                    if(snipCount == 6){break;}
                    snip = snip.trim();
                    
                    if(tempStr == null)
                    {
                        tempStr = snip;
                    }
                    else
                    {
                        tempStr = tempStr + ", " + snip;
                    }
                    snipCount ++;

                }
                //tfIdfClusterResults.add(pair.getValue()+" "+pair.getKey() + " - " + url.replace("%%", "/") + "\n" + tempStr);
                //tfIdfClusterResults.add(pair.getValue()+" "+pair.getKey() + " - " + url.replace("%%", "/") + "\n" + tempStr);
                //System.out.println(tempStr);
                
                UIResults.put("CID", String.valueOf(pair.getValue()));
                UIResults.put("DID", String.valueOf(pair.getKey()));                
                UIResults.put("SNIP", tempStr);
                UIResults.put("HREF", url.replace("%%", "/"));
                
                System.out.println("CID " +  String.valueOf(pair.getValue()));
                System.out.println("DID " +  String.valueOf(pair.getKey()));
                System.out.println("SNIP " +  tempStr);

                
                UIResultsList.add(UIResults);
                UIResults = new HashMap<String, String>();

            }
            else
            {
                continue;
            }
                
            //Collect keywords to describe a cluster
            if(clusterKeyWords.containsKey(docClusterMap.get(pair.getKey())))
            {
                tempSet = clusterKeyWords.get(docClusterMap.get(pair.getKey()));            
            }
            else
            {
                tempSet = new HashSet<String>();
            }
            wordcount = 0;

            
            for(String keyword: docKeyWordsMap.get(pair.getKey()))
            {
                if (wordcount == 10) break;
                tempSet.add(keyword);
                wordcount ++;
            }
            
                     
            clusterKeyWords.put(docClusterMap.get(pair.getKey()), tempSet);
            
       }
        
        System.out.println("Cluster description");
        for(Map.Entry<Integer, Set<String>> clusterDesc: clusterKeyWords.entrySet())
        {
            System.out.println("Cluster Id  " + clusterDesc.getKey());
            System.out.println(clusterDesc.getValue());
            
            //tfIdfClusterResults.add("Cluster Id  " + clusterDesc.getKey() + "\n" + clusterDesc.getValue());
            UIResults = new HashMap<String, String>();
            UIResults.put("DID", String.valueOf(-1));
            UIResults.put("CID", String.valueOf(clusterDesc.getKey()));
            
            String clusterSnippet=null;

             for(String clusterSnip : clusterDesc.getValue())
            {
                if(clusterSnippet == null)
                {
                    clusterSnippet = clusterSnip;
                }
                else
                {
                    clusterSnippet = clusterSnippet + "  -  " + clusterSnip;
                }   
            }
             
             
            UIResults.put("SNIP", clusterSnippet);
            UIResultsList.add(UIResults);
            //UIResults.clear();            
        }
    }
    
    
    List<String> getNeighbourWords(Integer docNum, String url)
    {
        
        //Read the given file, collect the neighbor terms of the every input query word and return it
        String htmlFile = htmlFilePath + url;
        String line;
        int indexCount=0;
        List<String> tempList = new ArrayList<String>();
        List<String> neighborList = new ArrayList<String>();
        String temp = "";
        
        try
        {
            BufferedReader br = new BufferedReader(new FileReader(htmlFile));
            //System.out.println("Html file " + htmlFile);
            while ((line = br.readLine()) != null) 
            {
                line = line.toLowerCase();
                inputQuery = inputQuery.toLowerCase();
            
                //As the index is created over the cleaned input files, cleaning at this part is not necessary
                line  = Jsoup.parse(line).text();
            
                
                tempList = Arrays.asList(line.split(" "));
                for (String queryWord : inputQuery.split(" "))
                {
                    if (tempList.contains(queryWord))
                    {                       
                            indexCount = tempList.indexOf(queryWord);
                            
                            for(int inIndex=indexCount-4;inIndex<indexCount+3;inIndex++)
                            {
                                try
                                {
                                    temp = temp + " " + tempList.get(inIndex);
                                }
                                catch(Exception e)
                                {
                                    continue;
                                }
                            }
                            
                            //Cleaning document snippet
                            temp = temp.replaceAll("\\<.*?>","");
                            //temp = temp.replaceAll("[^a-zA-Z ]", " ");
                            
                            neighborList.add(temp);
                            temp = " ";
                    }
                }
               // process the line.
            }
            br.close();
        }
        catch(Exception e)
        {
            System.out.println("Exception is " + e);
        }
            
        return neighborList;
    }
    
    Set<String> getKeyWords(Integer docNum)
    {
        Map<String, Float> tempDocWords = new HashMap<String, Float>();
        Set<String> tempSet = new HashSet<String>(); 
        int loopVar = 0;
        
        tempDocWords = docWordsMap.get(docNum);
        
         KeyWordsComparator bvc =  new KeyWordsComparator(tempDocWords);
         TreeMap<String, Float> sortedMap = new TreeMap<String, Float>(bvc);
         sortedMap.putAll(tempDocWords);
        
         for(Map.Entry<String, Float> pair: sortedMap.entrySet())
         {
             if (loopVar < 10)
             {
                 tempSet.add(pair.getKey());
                 loopVar++;
             }
             else
             {
                 break;
             }
         }
        return tempSet;
    }
    
    public void resultsClustering(SearchFiles sObj, int resultsCount) throws Exception
    {
        //Cluster the documents results present in TfIdfResults - KMeans
        
        int initialSeed; 
        float diff = 1;
        int docNum = 0;
        float curSSE = 0;
        float maxSSE = 0;
            
        List<Map<String, Float>> centroidList = new LinkedList<Map<String, Float>>();
        List<Map<String, Float>> newCentroidList = new LinkedList<Map<String, Float>>();
        List<Map<String, Float>> oldCentroidList = new LinkedList<Map<String, Float>>();
        
        Map<Integer, Integer> docClusterMap = new HashMap<Integer, Integer>(); 
        Map<Integer, Integer> bestDocClusterMap = new HashMap<Integer, Integer>();
        
        for(int g =0 ; g < 1; g++)
        {   
            //Get K different initial seeds  randomly 
            Set<Integer> selectedSeeds = new HashSet<Integer>();
            diff = 1;
            for(int k=0; k<sObj.kSize; k++)
            {
                while(true)
                {
                    initialSeed = (int)(Math.random() * (resultsCount-1));
                
                    if (selectedSeeds.contains(initialSeed))
                    {
                        continue;
                    }
                    else
                    {
                        break;
                    }
                }   
                docNum = Integer.parseInt(sObj.TfIdfResults.get(initialSeed));
                centroidList.add(sObj.docWordsMap.get(docNum));
                selectedSeeds.add(initialSeed);
            }   
            
            Map<String, Float> docVectorMap;
    

        //Iterate over the TfIdf results to find out which cluster each one belongs to.
            while(diff > 0.01)
            {
                float maxSim,curSim;
                Integer maxIndex= 0;
                Integer index;
                Map<Integer, Map<String, Float>> newCentroidMap = new HashMap<Integer, Map<String, Float>>();
                Map<String, Float> curCentroidMap; 
                Map<Integer, Integer> newCentroidInstanceCountMap = new HashMap<Integer, Integer>();
                float curTfIdfVal;
                int instanceCount;
   
                for(String docId: sObj.TfIdfResults)
                {
                    docNum = Integer.parseInt(docId);
                    maxSim = 0;
                    maxIndex = -1;
                    docVectorMap = sObj.docWordsMap.get(docNum);        
            
                    //Iterate over all the centroids to find out the one closest to this instance 
                    index = 0;
                    for(Map<String, Float> centroidVectorMap: centroidList)
                    {
                        if (centroidVectorMap == null)continue;
                        curSim = sObj.findVectorSimilarity(docNum, docVectorMap, centroidVectorMap, sObj);
                        if(curSim > maxSim)
                        {
                            maxSim = curSim;
                            maxIndex = index;                   
                        }
                        index++;
                    }
                    docClusterMap.put(docNum, maxIndex);
                    
                    //New centroid values computation simultaneously
                    if (newCentroidMap.containsKey(maxIndex))
                    {
                        curCentroidMap = newCentroidMap.get(maxIndex);
                    }
                    else
                    {
                        curCentroidMap = new HashMap<String, Float>();
                    }
                    
                    for(Map.Entry<String, Float> docEntry: docVectorMap.entrySet())
                    {
                        
                        if(curCentroidMap.containsKey(docEntry.getKey()))
                        {
                            curTfIdfVal = curCentroidMap.get(docEntry.getKey());                    
                        }
                        else
                        {
                            curTfIdfVal = 0;
                        }
                        curTfIdfVal =  curTfIdfVal + docEntry.getValue();
                        curCentroidMap.put(docEntry.getKey(), curTfIdfVal);                 
                    }
                    newCentroidMap.put(maxIndex, curCentroidMap);
            
                    //Increment the new centroid instance count
                    if (newCentroidInstanceCountMap.containsKey(maxIndex))
                    {
                        instanceCount = newCentroidInstanceCountMap.get(maxIndex);
                    }
                    else
                    {
                        instanceCount = 0;
                    }
                    instanceCount = instanceCount + 1;
                    newCentroidInstanceCountMap.put(maxIndex, instanceCount);       
                }
            
                //Find new centroids 
        
                int clusterCount = 0;
        
                for(Map.Entry<Integer, Map<String, Float>> centroidVector : newCentroidMap.entrySet())
                {    
                    clusterCount = newCentroidInstanceCountMap.get(centroidVector.getKey());
                    curCentroidMap = centroidVector.getValue();
                    for(Map.Entry<String, Float> centroidWordsVector : centroidVector.getValue().entrySet())
                    {
                        curTfIdfVal = centroidWordsVector.getValue();
                        curTfIdfVal = curTfIdfVal % clusterCount;           
                        curCentroidMap.put(centroidWordsVector.getKey(), curTfIdfVal);
                    }
                    newCentroidMap.put(centroidVector.getKey(), curCentroidMap);
                }
        
                //Update the new centroids
                for(int i=0; i<kSize;i++)
                {
                    newCentroidList.add(newCentroidMap.get(i));
                }
        
                //Check for convergence
                List<Float> diffList = new LinkedList<Float>();
        
                for(int i=0; i<kSize; i++)
                {
                    diff = getWordVectorMaxDiff(centroidList.get(i), newCentroidList.get(i));
                    diffList.add(diff);
                } 
                diff = Collections.max(diffList);   
                System.out.println("Diff is " +  diff);
                //System.out.println(newCentroidInstanceCountMap);

        
                for(int z=0;z<kSize;z++)
                {
                    oldCentroidList.add(null);
                }
            
                Collections.copy(centroidList, newCentroidList);
                newCentroidList.clear();
            }
            //curSSE = sObj.findSSE(docClusterMap, centroidList, sObj);
            System.out.println("Current SSE  " + curSSE);
            if (curSSE > maxSSE)
            {
                bestDocClusterMap.putAll(docClusterMap);
                maxSSE = curSSE;
            }
        }
        sObj.displayClusters(docClusterMap);
    }
    
    Float findSSE(Map<Integer, Integer> docClusterMap, List<Map<String, Float>> oldCentroidList, SearchFiles sObj)
    {
        //Used similarity condition to find out the tightness of the cluster
        //High similarity corresponds to high tightness
        
        Map<Integer, Float> sseMap = new HashMap<Integer, Float>();
        float sseVal=0;
        
        for(Map.Entry<Integer, Integer> docInstanceMap: docClusterMap.entrySet())
        {
            if(sseMap.containsKey(docInstanceMap.getValue()))
            {
                sseVal = sseMap.get(docInstanceMap.getValue());
            }
            else
            {
                sseVal = 0;
            }
            sseVal = sseVal + sObj.getWordVectorMaxDiff(sObj.docWordsMap.get(docInstanceMap.getKey()), oldCentroidList.get(docInstanceMap.getValue()));
            sseMap.put(docInstanceMap.getValue(), sseVal);
        }
        
        sseVal = 0;

        for(Map.Entry<Integer, Float> sseInstance: sseMap.entrySet())
        {
            sseVal  = sseVal + sseInstance.getValue();
        }
        System.out.println("SSE VALUE IS" + Math.sqrt(sseVal));
        return (float) Math.sqrt(sseVal);
    }
    
    float getWordVectorMaxDiff(Map<String, Float> oldCentroidVector, Map<String, Float> newCentroidVector)
    {       
        if (oldCentroidVector == null || newCentroidVector == null)
            return 0;
        
        Set<String> allWordsSet  = new HashSet<String>();
        Set<String> centroidWordsSet  = new HashSet<String>();
        
        float curDiff = 0;
        float maxDiff = 0;
        Float oldTfIdf, newTfIdf ;
        
        //Add centroid vector words to all words set
        
        for(String word: oldCentroidVector.keySet())
        {
            allWordsSet.add(word);
        }
        
        for(String word: newCentroidVector.keySet())
        {
            allWordsSet.add(word);
        }
        
        
        for(String word: allWordsSet)
        {
            oldTfIdf = oldCentroidVector.get(word);
            newTfIdf = newCentroidVector.get(word);
            
            if(oldTfIdf == null && newTfIdf != null)
            {
                curDiff = newTfIdf;
            }
            else if (oldTfIdf != null && newTfIdf == null)
            {
                curDiff = oldTfIdf;
            }
            else
            {
                curDiff = Math.abs(newTfIdf - oldTfIdf);
            }
            
            if (curDiff > maxDiff)
            {
                 maxDiff  = curDiff;
            }
        }
        
        return maxDiff;
    }
    
    Float scalarVectorSimilarity(Map<String, Float> firstTermMap,  Map<String, Float> secondTermMap)
    {
        // Find the vector similarity of given two vectors
        
        float sim = 0, deno = 0;
        Set<String> allWordsSet  = new HashSet<String>();

        for(String word : firstTermMap.keySet())
        {
            allWordsSet.add(word);
        }
        
        for(String word : secondTermMap.keySet())
        {
            allWordsSet.add(word);
        }
        
        for(String word: allWordsSet)
        {
            if(firstTermMap.containsKey(word) && secondTermMap.containsKey(word))
            {
                sim = sim + firstTermMap.get(word) * secondTermMap.get(word);
            }
        }
        
        deno = findCentroidTwoNorm(firstTermMap) * findCentroidTwoNorm(secondTermMap);      
        return (sim/deno);          
    }
    
    
    Float findVectorSimilarity(Integer docNum, Map<String, Float> docVectorMap, Map<String, Float> centroidVectorMap, SearchFiles sObj)
    {
        Set<String> allWordsSet  = new HashSet<String>();
        Set<String> centroidWordsSet = new HashSet<String>();
        float sim = 0;
        float deno = 0;
                
        //Add centroid vector words to all words set
        
        for(String word: docVectorMap.keySet())
        {
            allWordsSet.add(word);
        }
        
        for(String word: centroidVectorMap.keySet())
        {
            allWordsSet.add(word);
        }
        
        for(String word: allWordsSet)
        {
            if(docVectorMap.containsKey(word) && centroidVectorMap.containsKey(word))
            {
                sim = sim + docVectorMap.get(word) * centroidVectorMap.get(word);
            }
        }
        
        deno = (float) (sObj.twoNormTfIdf.get(docNum) * sObj.findCentroidTwoNorm(centroidVectorMap));       
        return (sim/deno);
    }
    
    Float findCentroidTwoNorm(Map<String, Float> centroidVectorMap)
    {
        float twoNorm = 0;
        for(Map.Entry<String, Float> pair: centroidVectorMap.entrySet())
        {
            twoNorm = twoNorm + pair.getValue() * pair.getValue();
        }
        return (float) Math.sqrt(twoNorm);
    }
    
    public void pageRankOrdering(Map<String, Double> relMap, IndexReader r, SearchFiles sObj) throws Exception
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
        
        //Relevance value normalization to the interval [0,1] 
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
             Document d = r.document(Integer.parseInt(pair.getKey()));
             String url = d.getFieldable("path").stringValue();
             sObj.PageRankResults.add(url);
             //System.out.println(pair.getKey() + " - " + url.replace("%%", "/"));
             System.out.println(pair.getKey());
             if (loopLimit == resultsCount)
                 break;
         }
    }
    
    public void computeAuthorityHub(Map<String, Double> rootSet, IndexReader r, SearchFiles sObj) throws Exception
    {
        long startTime = System.currentTimeMillis();

        LinkAnalyses.numDocs = 25054;
        LinkAnalyses la = new LinkAnalyses();

        System.out.println("Inside compute Authority Hub");
        //Form base set  from the root set
        
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
        
        //Data structures to store alias for original doc numbers 
        HashMap<Integer, Integer> origDocToAliasDocMap = new HashMap<Integer, Integer>();
        HashMap<Integer, Integer> aliasDocToOrigDocMap = new HashMap<Integer, Integer>();
        
        //Size of base set
        int docCount = baseSet.size();
        System.out.println("Base set  " + docCount);
        
        int count = 0;
        for(Integer docNum:baseSet)
        {
            origDocToAliasDocMap.put(docNum, count);
            aliasDocToOrigDocMap.put(count, docNum);
            count ++;
        }
    
        //Adjacency matrix data structures
        int [][] adjMatrix = new int[docCount][docCount];
        int [][] adjMatrixTrans = new int[docCount][docCount];
        int [][] authMatrix = new int[docCount][docCount];
        int [][] hubMatrix = new int[docCount][docCount];
        
        double [] authVector = new double[docCount];
        double [] authTempVector = new double[docCount];
        double [] hubVector = new double[docCount];
        double [] hubTempVector = new double[docCount];       
 
        //Initialize the authVector to 1
        
        Arrays.fill(authVector, 1.0);
        Arrays.fill(hubVector, 1.0);
        
        //Adjacency matrix construction
        for(Integer docNum:baseSet)
        {
            int links[] = la.getLinks(docNum);
            for(int link:links)
            {
                if (baseSet.contains(link) && baseSet.contains(docNum))
                {
                    //Populate adj matrix with alias doc ids
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

        
        // -------------Sort and display top K authority results----------------------
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
            if (authCount == 10)break;
            Document d = r.document(Integer.parseInt(pair.getKey()));
            String url = d.getFieldable("path").stringValue();
            sObj.AHResults.add(url);
            System.out.println(pair.getKey() + " - " + url.replace("%%", "/"));
            authCount++;
        }
        endTime = System.currentTimeMillis();
        System.out.println("Time taken to find top ten authority "+ (double)(endTime - startTime)/1000);
        
        
     // -----------------Sort and display top K hub results----------------------
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
            if (hubCount == 10)break;
            Document d = r.document(Integer.parseInt(pair.getKey()));
            String url = d.getFieldable("path").stringValue();
            sObj.AHResults.add(url);
            System.out.println(pair.getKey() + " - " + url.replace("%%", "/"));
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
    
    
    public int findCharDiff(String pWord, String nWord)
    {
        
        //Unigram intersection check
        
        //System.out.println("Inside finding char diff");
        Set<Character> pCharSet = new HashSet<Character>();
        Set<Character> nCharSet = new HashSet<Character>();
        
        for(int i=0; i<pWord.length(); i++)
        {
            pCharSet.add(pWord.charAt(i));
        }
        
        //System.out.println(pCharSet);
        
        for(int i=0; i<nWord.length(); i++)
        {
            nCharSet.add(nWord.charAt(i));
        }
        //System.out.println(nCharSet);

        if(Math.abs(pCharSet.size() - nCharSet.size()) > 2)
        {
            return 0;
        }
        
        pCharSet.removeAll(nCharSet);
        //System.out.println(pCharSet);
        if (pCharSet.size() < 2)
        {
            return 1;
        }
        
        return 0;
    }
    
    public int  findWordDist(String pWord, String nWord, SearchFiles sObj)
    {
        
        //Bigram intersection check
        
        if (sObj.findCharDiff(pWord, nWord) == 0)
        {
            return -1;
        }
        
        //Find the  distance between two words
        
        Set<String> pWordSet = new HashSet<String>();
        Set<String> nWordSet = new HashSet<String>();
        
        String temp;
        for(int i=0; i+1<pWord.length(); i++)
        {
        temp = pWord.substring(i, i+2);
        pWordSet.add(temp);
        }
        //System.out.println(pWordSet);
        
        for(int i=0; i+1<nWord.length(); i++)
        {
            temp = nWord.substring(i, i+2);
            nWordSet.add(temp);
        }
        //System.out.println(nWordSet);
        
        pWordSet.removeAll(nWordSet);
        //System.out.println(pWordSet);

        //System.out.println(pWordSet.size());
        return pWordSet.size();
    }
    
    public int getMinimum(int a, int b, int c)
    {
        return Math.min(Math.min(a, b), c);
    }
    
    public int computeLevenDistance(String str1, String str2, SearchFiles sObj)
    {
        int[][] distance = new int[str1.length() + 1][str2.length() + 1];
         
        for (int i = 0; i <= str1.length(); i++)
            distance[i][0] = i;
        for (int j = 1; j <= str2.length(); j++)
            distance[0][j] = j;
 
        for (int i = 1; i <= str1.length(); i++)
            for (int j = 1; j <= str2.length(); j++)
                distance[i][j] = sObj.getMinimum(
                        distance[i - 1][j] + 1,
                        distance[i][j - 1] + 1,
                        distance[i - 1][j - 1]+ ((str1.charAt(i - 1) == str2.charAt(j - 1)) ? 0 : 1));
 
        return distance[str1.length()][str2.length()];    
    }
    
    public String findBestWord(Set<String> possibleCand) throws Exception
    {
        System.out.println("Find best word");
        int maxFreq = 0, freq=0;
        String bestWord="";
        
        for(String word : possibleCand)
        {
            Term term = new Term("contents", word);
            TermDocs tdocs = r.termDocs(term);
            freq = 0;
            while(tdocs.next())
            {
                freq += tdocs.freq();
            }
            if(freq > maxFreq)
            {
                maxFreq = freq;
                bestWord = word;
            }
        }
        
        return bestWord;
    }
    
    public String handleMisspeltWords(String misSpeltWord, SearchFiles sObj) throws Exception
    {

        int minDist = 100000;
        String finalWord = "";
        int dist;
        int pLength = misSpeltWord.length();
        int levenDist = 0;
        Set<String> possibleCand = new HashSet<String>();
        Set<String> secondaryCand = new HashSet<String>();
        
        for(String term:termList)
        {
            //System.out.println(term);
            
            //Length check
            if(Math.abs(term.length() - pLength)>3)
            {
                continue;
            }
            dist =  sObj.findWordDist(term, misSpeltWord, sObj);
            if(dist == -1)
            {
                continue;
            }
            //System.out.println(term);
            
            
            levenDist = sObj.computeLevenDistance(term, misSpeltWord, sObj);
            if(levenDist == 1)
            {   
                possibleCand.add(term);
            }   
            if(levenDist == 2)
            {
                secondaryCand.add(term);
            }
            
        }
        finalWord = sObj.findBestWord(possibleCand);
        if(finalWord == "")
        {
            finalWord = sObj.findBestWord(secondaryCand);
        }
        return finalWord;
    }
    
    public void orderUsingTfIdf(String str, IndexReader r, SearchFiles sObj, Map<String, Double> relMapTfIdf) throws Exception
    {
        //Find the relevant results using TfIdf based vector similarity between query and docs
        
        long startTime = System.currentTimeMillis();

        String[] terms = str.split("\\s+");
        
        int queryLen = terms.length;
        double relTfIdf;
        double Idf;
        int totalDocs = r.maxDoc();
        String docid;
             
        for(String word : terms)
        {
            System.out.println("Query words " + word);
            Term term = new Term("contents", word);
            TermDocs tdocs = r.termDocs(term);
            
            //Handling misspelt words
            if(!sObj.termList.contains(word))
            {
                System.out.println("No match found for this word");
                word = sObj.handleMisspeltWords(word, sObj);
                System.out.println("Did You Mean  " + word);
                term = new Term("contents", word);
                tdocs = r.termDocs(term);
            }

            while(tdocs.next())
            {
                if (tdocs.doc() > sObj.docSize)
                {
                    continue;
                }
                relTfIdf = 0;           
                docid = Integer.toString(tdocs.doc());
                if(relMapTfIdf.containsKey(docid))
                {
                    relTfIdf = relMapTfIdf.get(docid);
                }
                
                Idf = (double)(totalDocs/(double)r.docFreq(term));
                Idf = Math.log(Idf)/Math.log(2);
                //System.out.println("Idf  " + Idf);
                relTfIdf += (double)(tdocs.freq() * Idf )/((Math.sqrt(queryLen) * Math.sqrt(sObj.twoNormTfIdf.get(tdocs.doc()))));
                relMapTfIdf.put(docid, relTfIdf);   
                
            }
        }
        System.out.println(" Total number of results of TfIdf " + relMapTfIdf.size());
        sObj.showResults(relMapTfIdf, r, sObj);
        long endTime = System.currentTimeMillis();
        System.out.println("Ordering based on TfIdf results -Time Taken "+ (double)(endTime - startTime)/1000);
    }
   
    public void getMemoryUsage()
    {
        // To find the current memeory usage
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
        
        //Page rank ordering
        long startTime = System.currentTimeMillis();
        getMemoryUsage();

        LinkAnalyses.numDocs = 25054;
        LinkAnalyses la = new LinkAnalyses();

        int docCount = 25054;
        int nonZeroCount = 0;
        double sinkValue = (double)1/docCount;
        double kValue = (double)(1-cProb) * sinkValue;
        System.out.println("C prob is " + cProb );
        double temp = 0;

        double [] tempPageRankVector = new double[docCount];
        double [] markovMatrix = new double[docCount];
        int [] row = new int[docCount];
        int [] tempRow = new int[docCount];
        int [] column = new int[docCount];

        Map<Integer, Integer> nonZeroCountHash = new HashMap<Integer, Integer>();       

        //Setting initial page rank vector values to be 1
        Arrays.fill(pageRankVector, sinkValue);

        //Fill the hash with non zero count
        for(int i=0; i< docCount; i++)
        {
            nonZeroCount = 0;
            column = la.getLinks(i);
        
            nonZeroCount = column.length;
            nonZeroCountHash.put(i, nonZeroCount);
            column = new int[docCount];
        }

        int converge = 0;
        int convergeCount = 0;
        
        //Page rank power iteration
        while(converge == 0)
        {
            convergeCount++;
         
            //Single matrix * vector multiplication
            System.out.println("Trying to converge  " + convergeCount);
            for(int i=0; i < docCount; i++)
            {
                tempRow = la.getCitations(i);
                Arrays.fill(row, 0);
                
                //populate the row with non zero values
               
                for(int tRow:tempRow)
                {
                    row[tRow] = 1;
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

                //markovMatrix row  * page rank vector 
                for(int l=0; l<docCount; l++)
                {
                   temp += markovMatrix[l] * pageRankVector[l]; 
                }
                tempPageRankVector[i] = temp;
                 
                //System.out.println("Index " + i + " value " + temp);
                temp = 0; 
            }

            
            double diff;

            //Checking for convergence
            for(int i=0; i<docCount; i++)
            {
                diff = Math.abs(tempPageRankVector[i] - pageRankVector[i]);
                
                if(diff <= sObj.pageRankThreshold || diff == 0)
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
        //Normalize page rank to the limit [0,1]
        
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
                maxPageRankIndex = i;

            }
            
            if (pageRankMin > pageRankVector[i])
            {
                pageRankMin = pageRankVector[i];
                minPageRankIndex = i;

            }
            
        }
        
        System.out.println("Page Rank after normalizing");
        System.out.println("Page Rank Max is " + pageRankMax + "Doc id  - " + maxPageRankIndex);
        System.out.println("Page Rank Min is " + pageRankMin + "Doc id - " + minPageRankIndex );

        Document d = r.document(maxPageRankIndex);
        String url = d.getFieldable("path").stringValue();
        System.out.println("Url is "+ url.replace("%%", "/"));
       
    }

    
    public  List<Map<String, String>> servletCall(String query, String method, int QEval) throws Exception
    {
        
        //Call from servlet handled here
        
        System.out.print("Inside sevletCalllll - beginning");
        query = query.trim();
        System.out.print("Query  "+ query + "  method " + method );
        SearchFiles sObj = new SearchFiles();
        sObj.r = IndexReader.open(FSDirectory.open(new File(indexPath)));
        
        //Compute two norm for all the documents - Both for Tf and TfIdf
        sObj.getTwoNorm(sObj.r, sObj);
        System.out.println("Two norm value for 845 " + Math.sqrt(sObj.twoNormTfIdf.get(845)));
               
        if (QEval == 1)
        {
            sObj.queryElborate = 1;
            sObj.resultsCount = 15;
        }
                
            long startTime = System.currentTimeMillis();
            query = query.toLowerCase();
            
            sObj.inputQuery = query;
          
            HashMap<String, Double> relMapTfIdf = new HashMap<String, Double>();        
            sObj.orderUsingTfIdf(query, sObj.r, sObj, relMapTfIdf);
            
            long endTime = System.currentTimeMillis();
            
            System.out.println("Time taken to get results "+ (double)(endTime - startTime)/1000);
      
            System.out.println("Query "+ query + " method " + method );    
            
        /**if(method.equals("PR"))
        {
            System.out.println("Inside PR method");
            System.out.println("Returning results of size " + sObj.PageRankResults.size());
            return sObj.PageRankResults;
        }
        else if (method.equals("AH"))
        {
            System.out.println("Inside AH method");
            System.out.println("Returning results of size " + sObj.AHResults.size());
            return sObj.AHResults;
        }**/
        if (method.equals("VS"))
        {
            System.out.println("Inside VS method");
            System.out.println("Returning results of size " + sObj.TfIdfResults.size());
            //return sObj.TfIdfResults;
            return sObj.UIResultsList;

        }
        
        else if(sObj.queryElborate == 1)
        {
            return sObj.UIResultsList;
        }
            
       System.out.print("Inside sevletCalllll - end");
    return sObj.UIResultsList;

    }
    
    public static void main(String[] args) throws Exception
    {
        SearchFiles sObj = new SearchFiles();
        sObj.r = IndexReader.open(FSDirectory.open(new File(sObj.indexPath)));
        
        //Compute two norm for all the documents - Both for Tf and TfIdf
        sObj.getTwoNorm(sObj.r, sObj);
        
        //PageRank computation
        //sObj.computePageRank(sObj, sObj.r);
        //sObj.getMemoryUsage();
        System.out.print("Page Rank Computation is done");
        
        
        Scanner sc = new Scanner(System.in);
        String str = "";
        System.out.print("query> ");
        String [] parts;
        
        
        while(!(str = sc.nextLine()).equals("quit"))
        {   
            long startTime = System.currentTimeMillis();
            str = str.toLowerCase();
           
            //Assign the input query
            sObj.inputQuery = str;
            
            //To handle W Threshold value in query time 
            System.out.println(str);
            if (str.startsWith("wthreshold"))
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
            sObj.orderUsingTfIdf(str, sObj.r, sObj, relMapTfIdf);
            
            long endTime = System.currentTimeMillis();
            
            System.out.println("Time taken to get results "+ (double)(endTime - startTime)/1000);
            System.out.print("query> ");
        }
        sc.close();
    }   
} 

