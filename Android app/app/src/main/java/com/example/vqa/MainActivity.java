package com.example.vqa;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;


public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_IMAGE_CAPTURE = 100 ;
    private static final int REQ_CODE_SPEECH_INPUT = 15;
    private Button capture_button;
    private ImageView imageView;
    private EditText question_editText;
    private TextView answer_textView;
    private Vocab questionVocab;
    private Vocab answerVocab;
    private String question_sentence;
    private int[] question_vector;
    VQA_test2 answer_classifier;
    private TextToSpeech speaker;
    private String currentImagePath;
    Bitmap rotatedBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        capture_button = (Button)findViewById(R.id.capture_button);
        imageView = (ImageView)findViewById(R.id.image);
        question_editText = (EditText)findViewById(R.id.question_edittext);
        answer_textView = (TextView)findViewById(R.id.answer_textview);

        questionVocab = new Vocab(this, "question_vocabs.txt");
        answerVocab = new Vocab(this, "annotation_vocabs.txt");

        speaker = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != TextToSpeech.ERROR){
                    speaker.setLanguage(Locale.US);
                }
            }
        });

        try {
            answer_classifier = new VQA_test2(this);
        } catch (IOException e) {
            e.printStackTrace();
        }


        if (!hasCamera())
            capture_button.setEnabled(false);

        capture_button.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            launchCamera();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
        );
    }

    private boolean hasCamera(){
        return getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
    }

    private void launchCamera() throws IOException {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File imageFile = getImageFile();
        Uri imageUri = FileProvider.getUriForFile(this, "com.example.vqa.fileprovider", imageFile);
        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
    }

    private File getImageFile() throws IOException{
        String imageName = "image";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File imageFile = File.createTempFile(imageName, ".jpg", storageDir);
        currentImagePath = imageFile.getAbsolutePath();
        return imageFile;
    }

/**
    private void launchCamera(){
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
    }
**/
    private void promptSpeechInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US);
        try {
            startActivityForResult(intent, REQ_CODE_SPEECH_INPUT);
        } catch (ActivityNotFoundException a) {
            Toast.makeText(getApplicationContext(),
                    "Sorry! Your device doesn't support speech input",
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode){
            case REQUEST_IMAGE_CAPTURE:
                {
                if (resultCode == RESULT_OK){
                    //Bitmap imageBitmap = getBitmapFromAsset("pic3.jpg");
                    Bitmap imageBitmap = BitmapFactory.decodeFile(currentImagePath);
                    Matrix matrix = new Matrix();
                    matrix.postRotate(90);
                    rotatedBitmap = Bitmap.createBitmap(imageBitmap, 0, 0, imageBitmap.getWidth(), imageBitmap.getHeight(), matrix, true);
                    imageView.setImageBitmap(rotatedBitmap);

                    promptSpeechInput();

                }
                break;
            }
            case REQ_CODE_SPEECH_INPUT:
                {
                    if (resultCode == RESULT_OK){
                ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                question_editText.setText(result.get(0));

                question_sentence = question_editText.getText().toString();
                Answer answer = answer_classifier.provideAnswer(this, rotatedBitmap, question_sentence);
                answer_textView.setText(answer.getTitle());
                speaker.speak(answer.getTitle(), TextToSpeech.QUEUE_FLUSH, null);
                }
            }
                break;
        }
    }


    private Bitmap getBitmapFromAsset(String strName)
    {
        AssetManager assetManager = getAssets();
        InputStream istr = null;
        try {
            istr = assetManager.open(strName);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Bitmap bitmap = BitmapFactory.decodeStream(istr);
        return bitmap;
    }
}