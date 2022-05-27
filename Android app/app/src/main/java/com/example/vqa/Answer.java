package com.example.vqa;

import android.graphics.RectF;

public class Answer implements Comparable {
    private final String title;
    private final Float confidence;

    public Answer(String title, Float confidence)
    {
        this.title = title;
        this.confidence = confidence;
    }

    public String getTitle(){return this.title;}
    public Float getConfidence(){return this.confidence;}

    @Override
    public String toString() {
        String resultString = "";

        if (title != null) {
            resultString += title + " ";
        }

        if (confidence != null) {
            resultString += String.format("(%.1f%%) ", confidence * 100.0f);
        }

        return resultString.trim();
    }

    @Override
    public int compareTo(Object o)
    {
        return Float.compare(((Answer)o).confidence, this.confidence);
    }
}

