package edu.asu.irs13;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import org.jsoup.Jsoup;

public class CleanHtmlFiles {
    
    String htmlFiles = "/Users/karthikchandrasekar/Desktop/SecondSem/IR/IR-NewIndex/IR-IndexedFiles/result3/";
    String cleanedHtmlFiles = "/Users/karthikchandrasekar/Desktop/SecondSem/IR/IR-NewIndex/IR-IndexedFiles/result3/cleanedHtmlFiles/";
    
    BufferedReader br = null;  
    BufferedWriter bw = null; 
    
    public CleanHtmlFiles()
    {
        
        
    }
        
    public void cleanHtmlFile(String fileName)
    {
        String line;
        String inputFile = htmlFiles + fileName;
        String outputFile = cleanedHtmlFiles + fileName;
                
        try
        {
        
            BufferedReader br = new BufferedReader(new FileReader(inputFile));
            BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile));
            
            
            while ((line = br.readLine()) != null) 
            {
                line = line.toLowerCase();      
                line  = Jsoup.parse(line).text();
                bw.write(line);  
                bw.write("\n");
                
            }
            br.close();
            bw.close();
        }
        
        catch (Exception e)
        {
            System.out.println("Problem will cleaning file " + inputFile);
        }       
    }
    
    
    
    public void listFilesForFolder(final File folder) {
        for (final File fileEntry : folder.listFiles()) {
            if (fileEntry.isDirectory()) {
                listFilesForFolder(fileEntry);
            } else {
                System.out.println(fileEntry.getName());
                cleanHtmlFile(fileEntry.getName());
                
            }
        }
    }

    
    
    public static void main(String args[])
    {
        CleanHtmlFiles cleanObj = new CleanHtmlFiles();
        
        final File folder = new File(cleanObj.htmlFiles);

        cleanObj.listFilesForFolder(folder);
    }
    

}

