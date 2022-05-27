package com.example.vqa;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class Vocab {
    private ArrayList<String> vocab = new ArrayList<String>();
    private BufferedReader reader = null;
    public int length = vocab.size();

    public Vocab(Context myContext, String filename) {
        try {
            reader = new BufferedReader(
                    new InputStreamReader(myContext.getAssets().open(filename), "UTF-8")
            );
            String line;
            while ((line = reader.readLine()) != null) {
                vocab.add(line);
            }
        }
        catch (IOException e) {
            Log.e("Vocab", "Error while creating the vocab: " + e);
        }
        try {
            reader.close();
        }
        catch (IOException e){
            Log.e("Vocab", "Error while closing the vocab:" + e);
        }
    }

    public int word2idx(String word){
        int id = vocab.indexOf(word);
        if (id < 0)
            id = 1;     //If the word is not in the question vocab, set it to <unk>
        return id;
    }

    public String idx2word(int id){
        return vocab.get(id);
    }
}
