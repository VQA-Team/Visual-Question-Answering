package com.example.vqa;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.label.TensorLabel;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VQA_test2 {
    private static final int MAX_RESULTS = 5;
    private final List<String> answers;
    private final Interpreter classifier;
    private final int img_resize_x;
    private final int img_resize_y;
    private final TensorBuffer inputQuestionTensor;
    private final TensorBuffer probability_tensor_buffer;
    private final ByteBuffer imgByteBuffer;
    private final float[] imageMean= {123.68f, 116.78f, 103.94f};  //{R, G, B}
    private final float[] imageStd= {1f, 1f, 1f};  //{R, G, B}

    private Vocab question_vocab;

    public VQA_test2(Activity activity) throws IOException {
        //Load the model into a MappedByteBuffer format
        MappedByteBuffer VQA_model = FileUtil.loadMappedFile(activity, "VQA_model.tflite");
        //Load the answers into a list of strings
        answers = FileUtil.loadLabels(activity, "annotation_vocabs.txt");

        //Create the interpreter object that performs inference with no options
        Interpreter.Options options = new Interpreter.Options();

        options.setNumThreads(4);
        classifier = new Interpreter(VQA_model, options);

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
        inputQuestionTensor = TensorBuffer.createFixedSize(input_question_shape, input_question_type);

        //Creates a ByteBuffer that will hold the image data and can be read by the interpreter
        //each pixel is represented by 3 colors, each of which is stored in 4 bytes (FLOAT32)
        imgByteBuffer = ByteBuffer.allocateDirect(img_resize_x * img_resize_y * 3 * 4);
        imgByteBuffer.order(ByteOrder.nativeOrder());

        probability_tensor_buffer = TensorBuffer.createFixedSize(output_props_shape, output_props_type);
    }


    public Answer provideAnswer(Context context, final Bitmap bitmap, String question_sentence) {
        List<Answer> answers_list = new ArrayList<>();
        loadImage(bitmap, imgByteBuffer);
        loadQuestion(context, question_sentence, inputQuestionTensor);

        Object[] inputArray = {inputQuestionTensor.getBuffer(), imgByteBuffer};

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
        //return the top prediction
        return answers_list.get(0);
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

    /*
    //This part pre-process the input image taken from the camera and turn it into a TensorImage
    private void loadImage(Bitmap bitmap, ByteBuffer imgBuffer) {

        TensorImage tensorImage = new TensorImage(DataType.FLOAT32);
        tensorImage.load(bitmap);
        ImageProcessor imageProcessor =
                new ImageProcessor.Builder()
                // Resize using Bilinear or Nearest neighbour
                .add(new ResizeOp(224, 224, ResizeOp.ResizeMethod.BILINEAR))
                .build();
        tensorImage = imageProcessor.process(tensorImage);
        Bitmap resizedBitmap = tensorImage.getBitmap();

        //An array that will hold the values stored in the bitmap
        int[] intValues = new int[img_resize_x * img_resize_y];
        //Returns in intValues[] a copy of the data in the bitmap. Each value is a packed int representing a Color(aRGB)
        resizedBitmap.getPixels(intValues, 0, resizedBitmap.getWidth(), 0, 0, resizedBitmap.getWidth(), resizedBitmap.getHeight());

        imgBuffer.rewind();
        //loop through all pixels
        for (int i = 0; i < img_resize_x; i++)
            for(int j = 0; j < img_resize_y; j++)
            {
                int pixelValue = intValues[i * img_resize_x + j];
                float R = (pixelValue >> 16) & 0xFF;
                float G = (pixelValue >> 8) & 0xFF;
                float B = pixelValue & 0xFF;
                imgBuffer.putFloat(B - imageMean[2]);   //B
                imgBuffer.putFloat(G - imageMean[1]);   //G
                imgBuffer.putFloat(R - imageMean[0]);   //R
            }
    }
*/
    //This part pre-process the input image taken from the camera and load it into a ByteBuffer
    private void loadImage(Bitmap bitmap, ByteBuffer imgBuffer) {

        Bitmap ResizedImage = Bitmap.createScaledBitmap(bitmap, img_resize_x, img_resize_y, true);
        //An array that will hold the values stored in the bitmap
        int[] intValues = new int[img_resize_x * img_resize_y];
        //Returns in intValues[] a copy of the data in the bitmap. Each value is a packed int representing a Color(aRGB)
        ResizedImage.getPixels(intValues, 0, ResizedImage.getWidth(), 0, 0, ResizedImage.getWidth(), ResizedImage.getHeight());

        imgBuffer.rewind();
        //loop through all pixels
        for (int i = 0; i < img_resize_x; i++)
            for(int j = 0; j < img_resize_y; j++)
            {
                int pixelValue = intValues[i * img_resize_x + j];
                float R = (pixelValue >> 16) & 0xFF;
                float G = (pixelValue >> 8) & 0xFF;
                float B = pixelValue & 0xFF;
                imgBuffer.putFloat(B - imageMean[2]);   //B
                imgBuffer.putFloat(G - imageMean[1]);   //G
                imgBuffer.putFloat(R - imageMean[0]);   //R
            }
        }
    }

