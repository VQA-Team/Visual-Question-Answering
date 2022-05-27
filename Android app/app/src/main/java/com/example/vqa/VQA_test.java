package com.example.vqa;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.label.TensorLabel;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VQA_test {
    private static final int MAX_RESULTS = 5;
    private final List<String> answers;
    private final Interpreter classifier;
    private final int img_resize_x;
    private final int img_resize_y;
    private final TensorImage inputImageTensor;
    private final TensorBuffer inputQuestionTensor;
    private final TensorBuffer probability_tensor_buffer;

    private Vocab question_vocab;

    public VQA_test(Activity activity) throws IOException {
        //Load the model into a MappedByteBuffer format
        MappedByteBuffer VQA_model = FileUtil.loadMappedFile(activity, "VQA_model.tflite");
        //Load the answers into a list of strings
        answers = FileUtil.loadLabels(activity, "annotation_vocabs.txt");
        //Create the interpreter object that performs inference with no options
        classifier = new Interpreter(VQA_model, null);

        int INPUT_QUESTION_INDEX = 0;
        int INPUT_IMAGE_INDEX = 1;
        int OUTPUT_PROP_INDEX = 0;

        int[] input_question_shape = classifier.getInputTensor(INPUT_QUESTION_INDEX).shape(); //{Batch_size, 30}
        DataType input_question_type = classifier.getInputTensor(INPUT_QUESTION_INDEX).dataType();

        int[] input_image_shape = classifier.getInputTensor(INPUT_IMAGE_INDEX).shape();     //{Batch_size, Height, Width, 3}
        DataType input_data_type = classifier.getInputTensor(INPUT_IMAGE_INDEX).dataType();

        int[] output_props_shape = classifier.getOutputTensor(OUTPUT_PROP_INDEX).shape();   //{Batch_size, Num_answers}
        DataType output_props_type = classifier.getOutputTensor(OUTPUT_PROP_INDEX).dataType();

        //Find the model's required input image size
        img_resize_x = input_image_shape[1];
        img_resize_y = input_image_shape[2];

        //creates tensors for the input and the output
        inputImageTensor = new TensorImage(input_data_type);
        inputQuestionTensor = TensorBuffer.createFixedSize(input_question_shape, input_question_type);
        probability_tensor_buffer = TensorBuffer.createFixedSize(output_props_shape, output_props_type);
    }


    public List<Answer> provideAnswer(Context context, final Bitmap bitmap, String question_sentence) {
        List<Answer> answers_list = new ArrayList<>();
        loadImage(bitmap, inputImageTensor);
        loadQuestion(context, question_sentence, inputQuestionTensor);

        Object[] inputArray = {inputQuestionTensor.getBuffer(), inputImageTensor.getBuffer()};

        Map<Integer, Object> outputMap = new HashMap<>();
        outputMap.put(0, probability_tensor_buffer.getBuffer().rewind());

        classifier.runForMultipleInputsOutputs(inputArray, outputMap);

        //returns a map that has each label and its corresponding probability
        Map<String, Float> labelled_props = new TensorLabel(answers, probability_tensor_buffer).getMapWithFloatValue();

        //Adds every element in the labelled probabilities to the list of Recognition
        for (Map.Entry<String, Float> entry : labelled_props.entrySet()) {
            answers_list.add(new Answer(entry.getKey(), entry.getValue()));
        }
        //Sorts the list of predictions based on confidence score
        Collections.sort(answers_list);
        //return the top 5 predictions
        answers_list = answers_list.subList(0, MAX_RESULTS);
        return answers_list;

    }


    private void loadQuestion(Context context, String sentence, TensorBuffer questionTensor) {
        question_vocab = new Vocab(context, "question_vocabs.txt");
        int max_question_length = 30;
        String[] tokens;
        int[] indices = new int[max_question_length];

        sentence = sentence.toLowerCase();
        tokens = sentence.split(" ");

        for (int i = 0; i < tokens.length; i++) {
            String word = tokens[i];
            word = word.replaceAll("[^a-zA-Z0-9]", ""); //remove non-alphabetic chars from each word
            if (word.length() > 0) {
                int index = question_vocab.word2idx(word);
                indices[i] = index;
            }
        }
        questionTensor.loadArray(indices);
    }

    //This part pre-process the input image taken from the camera and turn it into a TensorImage
    private void loadImage(Bitmap bitmap, TensorImage tensorImage) {
        tensorImage.load(bitmap);
    }

}
