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
		
		IndexReader r = IndexReader.open(FSDirectory.open(new File("index")));
		int totalDocs = r.maxDoc();
		System.out.println("The number of documents in this index is: " + r.maxDoc());

		int i = 0;
		TermEnum t = r.terms();

		HashMap<Integer, Integer> twoNorm = new HashMap<Integer, Integer>();
		
		while(t.next())
		{	
			Term te = new Term("contents", t.term().text());
			TermDocs td = r.termDocs(te);
			int freq;
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
		}
		
		
		Scanner sc = new Scanner(System.in);
		String str = "";
		System.out.print("query> ");
		HashMap<String, Double> relMapTfIdf = new HashMap<String, Double>();
		HashMap<String, Double> relMapTf = new HashMap<String, Double>();
		int queryLen;
		double relTfIdf;
		double relTf;
		double Idf;
		while(!(str = sc.nextLine()).equals("quit"))
		{
			String[] terms = str.split("\\s+");
			queryLen = terms.length;
			for(String word : terms)
			{
				Term term = new Term("contents", word);
				TermDocs tdocs = r.termDocs(term);
				while(tdocs.next())
				{
					relTf = 0;
					relTfIdf = 0;
					Idf = 0;
					
					if(relMapTfIdf.containsKey(tdocs.doc()))
					{
						relTfIdf = relMapTfIdf.get(tdocs.doc());
					}
					if(relMapTf.containsKey(tdocs.doc()))
					{
						relTf = relMapTf.get(tdocs.doc());
					}
					Idf = (double)(totalDocs/(double)r.docFreq(term));
					relTfIdf += (double)((tdocs.freq() * Idf )/(Math.sqrt(queryLen) * Math.sqrt(twoNorm.get(tdocs.doc()))));
					relMapTfIdf.put(Integer.toString(tdocs.doc()), relTfIdf);
					relTf += (double)(tdocs.freq())/(Math.sqrt(queryLen) * Math.sqrt(twoNorm.get(tdocs.doc())));
					relMapTf.put(Integer.toString(tdocs.doc()), relTf);
					String d_url = r.document(tdocs.doc()).getFieldable("path").stringValue().replace("%%", "/");
					
				}
			}
			
			//Override the comparator to sort the relMap by value. 
			ValueComparator bvc =  new ValueComparator(relMapTfIdf);
	        TreeMap<String,Double> sortedMapTfIdf = new TreeMap<String,Double>(bvc);
	        sortedMapTfIdf.putAll(relMapTfIdf);

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
	        	System.out.println(pair.getKey());
	        }

	        loopVar = 0;
	        System.out.println("Sorted using Tf/Idf");
	        for(Map.Entry<String, Double> pair : sortedMapTfIdf.entrySet())
	        {
	        	loopVar++;
	        	if(loopVar >9)
	        		break;
	        	System.out.println(pair.getKey());
	        }
			System.out.print("query> ");
		}
	}
}