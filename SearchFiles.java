package edu.asu.irs13;
import org.apache.lucene.index.*;
import java.util.*;

import org.apache.lucene.store.*;
import org.apache.lucene.document.*;

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
	public static void main(String[] args) throws Exception
	{
		// the IndexReader object is the main handle that will give you 
		// all the documents, terms and inverted index
		IndexReader r = IndexReader.open(FSDirectory.open(new File("index")));
		int totalDocs = r.maxDoc();
		// You can figure out the number of documents using the maxDoc() function
		System.out.println("The number of documents in this index is: " + r.maxDoc());
		
		int i = 0;
		// You can find out all the terms that have been indexed using the terms() function
		TermEnum t = r.terms();
		
		//Construct a idf map for every term
		HashMap<String, Double> idfMap = new HashMap<String, Double>();
		//System.out.println(t.term());
		
		while(t.next())
		{
			idfMap.put(t.term().text(), (double)(totalDocs/(double)t.docFreq()));
			System.out.println(t.docFreq());
		}
		
		// You can create your own query terms by calling the Term constructor, with the field 'contents'
		// In the following example, the query term is 'brute'
		Term te = new Term("contents", "brute");
		
		// You can also quickly find out the number of documents that have term t
		System.out.println("Number of documents with the word 'brute' is: " + r.docFreq(te));
		
		// You can use the inverted index to find out all the documents that contain the term 'brute'
		//  by using the termDocs function
		TermDocs td = r.termDocs(te);
		
		//while(td.next())
		//{
			//System.out.println("Document number ["+td.doc()+"] contains the term 'brute' " + td.freq() + " time(s).");
		//}
		
		// You can find the URL of the a specific document number using the document() function
		// For example, the URL for document number 14191 is:
		Document d = r.document(14191);
		String url = d.getFieldable("path").stringValue(); // the 'path' field of the Document object holds the URL
		//System.out.println(url.replace("%%", "/"));
		

		// -------- Now let us use all of the functions above to make something useful --------
		// The following bit of code is a worked out example of how to get a bunch of documents
		// in response to a query and show them (without ranking them according to TF/IDF)
		Scanner sc = new Scanner(System.in);
		String str = "";
		System.out.print("query> ");
		HashMap<String, Double> relMap = new HashMap<String, Double>();
		HashMap<String, Double> relMapTf = new HashMap<String, Double>();
		while(!(str = sc.nextLine()).equals("quit"))
		{
			String[] terms = str.split("\\s+");
			for(String word : terms)
			{
				Term term = new Term("contents", word);
				TermDocs tdocs = r.termDocs(term);
				while(tdocs.next())
				{
					//System.out.println(tdocs.freq());
					if (!idfMap.containsKey(word))
						continue;
					relMap.put(Integer.toString(tdocs.doc()), (double)(tdocs.freq() * idfMap.get(word)));
					relMapTf.put(Integer.toString(tdocs.doc()), (double)(tdocs.freq()));
					String d_url = r.document(tdocs.doc()).getFieldable("path").stringValue().replace("%%", "/");
					//System.out.println("["+tdocs.doc()+"] " + d_url);
					System.out.println(idfMap.get(word));
				}
				
				//Override the comparator to sort the relMap by value. 
				ValueComparator bvc =  new ValueComparator(relMap);
		        TreeMap<String,Double> sorted_map = new TreeMap<String,Double>(bvc);
		        sorted_map.putAll(relMap);
		        
		        ValueComparator bvcTf = new ValueComparator(relMapTf);
		        TreeMap<String,Double> sortedMapTf = new TreeMap<String,Double>(bvcTf);
		        sortedMapTf.putAll(relMapTf);
		        
		        //Print top ten high relevant elements
		        int loopVar =0;
		        
		        System.out.println("Sorted using Tf");
		        for(Map.Entry<String, Double> pair : sortedMapTf.entrySet())
		        {
		        	loopVar++;
		        	if(loopVar >9)
		        		break;
		        	//System.out.println(pair.getKey());
		        }
		        
		        loopVar = 0;
		        System.out.println("Sorted using Tf/Idf");
		        for(Map.Entry<String, Double> pair : sorted_map.entrySet())
		        {
		        	loopVar++;
		        	if(loopVar >9)
		        		break;
		        	//System.out.println(pair.getKey());
		        }
		        System.out.println(relMapTf);
				System.out.println(relMap);
		        
				System.out.println(sortedMapTf);
				System.out.println(sorted_map);
		        
				
		        loopVar = 0;
		        for(Map.Entry<String, Double> pair : idfMap.entrySet())
		        {
		        	loopVar ++;
		        	if(loopVar>10)
		        		break;
		        	System.out.println(pair);
		        }
				//System.out.println(idfMap);
			}
			
			System.out.print("query> ");
		}
	}
}
