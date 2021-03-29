package com.sanson.blindHelper

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import java.io.File
import java.lang.Exception
import java.util.*

const val TEXT_MESSAGE = "com.sanson.blindHelper.TEXT"

const val ENGLISH = " A1B'K2L@CIF/MSP\"E3H9O6R^DJG>NTQ,*5<-U8V.%[$+X!&;:4\\0Z7(_?W]#Y)=\n"
const val BRAILLE = "⠀⠁⠂⠃⠄⠅⠆⠇⠈⠉⠊⠋⠌⠍⠎⠏⠐⠑⠒⠓⠔⠕⠖⠗⠘⠙⠚⠛⠜⠝⠞⠟⠠⠡⠢⠣⠤⠥⠦⠧⠨⠩⠪⠫⠬⠭⠮⠯⠰⠱⠲⠳⠴⠵⠶⠷⠸⠹⠺⠻⠼⠽⠾⠿\n"
val MAP = ENGLISH.zip(BRAILLE).toMap()

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private var _selectIntent = 101
    private lateinit var imageViewMain: ImageView
    private lateinit var imageViewMainUri: Uri

    private var _captureIntent = 102
    private lateinit var captureFile: File

    private lateinit var mTTS: TextToSpeech
    private var _textResult = "textRes"
    lateinit var detectedText: com.google.mlkit.vision.text.Text

    private var _objectResult = "objectRes"
    lateinit var detectedObjects: String

    private var _sttIntent = 103

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageViewMain = findViewById(R.id.imageView_main)

        mTTS = TextToSpeech(this, this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && requestCode == _selectIntent) {
            imageViewMainUri = data?.data!!
            imageViewMain.setImageURI(imageViewMainUri)
        }

        if (resultCode == RESULT_OK && requestCode == _captureIntent) {
            imageViewMainUri = Uri.fromFile(captureFile)
            imageViewMain.setImageURI(imageViewMainUri)
        }

        if (resultCode == RESULT_OK && requestCode == _sttIntent && data != null) {
            val result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val text = result?.get(0)
            startCommand(text)
        }
    }

    private fun startCommand(text: String?) {
        if (text == "select image") {
            selectImage(imageViewMain)
        } else if (text == "capture image") {
            captureImage(imageViewMain)
        } else if (text == "detect text") {
            detectText(imageViewMain)
        } else if (text == "detect objects" || text == "detect objects") {
            detectObjects(imageViewMain)
        }
    }

    fun selectImage(view: View) {
        val selectImageIntent = Intent(Intent.ACTION_PICK)
        selectImageIntent.type = "image/*"
        startActivityForResult(selectImageIntent, _selectIntent)
    }

    fun captureImage(view: View) {
        val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        captureFile = getCaptureFile("braille.jpg")

        val fileProvider = FileProvider.getUriForFile(
            this,
            "com.sanson.blindHelper.fileprovider",
            captureFile
        )
        captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, fileProvider)
        startActivityForResult(captureIntent, _captureIntent)
    }

    private fun getCaptureFile(fileName: String): File {
        val storageDirectory = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(fileName, ".jpg", storageDirectory)
    }

    fun detectText(view: View) {
        showToast(getString(R.string.text_detection_start))
        saySomething(getString(R.string.text_detection_start))

        val image: InputImage
        try {
            image = InputImage.fromFilePath(this, imageViewMainUri)

            val recognizer = TextRecognition.getClient()
            recognizer.process(image)
                .addOnSuccessListener { text ->
                    // Task completed successfully
                    showToast(getString(R.string.text_detection_successful))
//                    showDetectTextResult(text)
                    detectedText = text
                    saySomething(getString(R.string.text_detection_successful))
                    saySomething(text.text, _textResult)
                }
                .addOnFailureListener { e ->
                    // Task failed with an exception
                    showToast(getString(R.string.text_detect_error))
                    e.printStackTrace()
                }
        } catch (e: Exception) {
            showToast(getString(R.string.text_detect_error))
            saySomething(getString(R.string.text_detect_error))
            e.printStackTrace()
        }
    }

    private fun showDetectTextResult(result: com.google.mlkit.vision.text.Text) {
        val textResultIntent = Intent(this, DisplayTextResultActivity::class.java).apply {
            putExtra(TEXT_MESSAGE, result.text)
        }
        startActivity(textResultIntent)
    }

    fun detectObjects(view: View) {
        showToast(getString(R.string.object_detection_start))
        saySomething(getString(R.string.object_detection_start))

        val image: InputImage
        try {
            image = InputImage.fromFilePath(this, imageViewMainUri)

            val localModel = LocalModel.Builder()
                .setAssetFilePath("lite-model_object_detection_mobile_object_labeler_v1_1.tflite")
                .build()

            val customObjectDetectorOptions =
                CustomObjectDetectorOptions.Builder(localModel)
                    .setDetectorMode(CustomObjectDetectorOptions.SINGLE_IMAGE_MODE)
                    .enableMultipleObjects()
                    .enableClassification()
                    .setClassificationConfidenceThreshold(0.5f)
                    .setMaxPerObjectLabelCount(3)
                    .build()

            val objectDetector = ObjectDetection.getClient(customObjectDetectorOptions)

            objectDetector.process(image)
                .addOnFailureListener { e ->
                    // Task failed with an exception
                    showToast(e.message.toString())
                }
                .addOnSuccessListener { detectedObjects ->
                    // Task completed successfully
                    speakDetectObjectsResult(detectedObjects)
                }
        } catch (e: Exception) {
            showToast(getString(R.string.error_detecting_objects))
            saySomething(getString(R.string.error_detecting_objects))
            e.printStackTrace()
        }
    }

    private fun speakDetectObjectsResult(results: MutableList<DetectedObject>) {
        val objectsString = StringBuilder()
        for (detectedObject in results) {
            for (label in detectedObject.labels) {
                objectsString.append(label.text)
                objectsString.append("\n")
            }
        }
        showToast(getString(R.string.object_detection_successful))
        saySomething(getString(R.string.object_detection_successful))

        detectedObjects = objectsString.toString()
        saySomething(detectedObjects, _objectResult)
    }

    private fun showObjectDetectionResult() {
        val textResultIntent = Intent(this, DisplayObjectDetectionResult::class.java).apply {
            putExtra(TEXT_MESSAGE, detectedObjects)
        }
        startActivity(textResultIntent)
    }

    private fun showToast(s: String, length: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, s, length).show()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            mTTS.language = Locale.getDefault()
            mTTS.setOnUtteranceProgressListener(object : UtteranceProgressListener() {

                override fun onDone(utteranceId: String?) {
                    //do whatever you want when TTS finish speaking.
                    if (utteranceId == _textResult) {
                        showDetectTextResult(detectedText)
                    }

                    if (utteranceId == _objectResult) {
                        showObjectDetectionResult()
                    }
                }

                override fun onError(utteranceId: String?) {
                    //do whatever you want if TTS makes an error.
                }

                override fun onStart(utteranceId: String?) {
                    //do whatever you want when TTS start speaking.
                }
            })
        } else {
            showToast("TTS initialization failed!!")
        }
    }

    private fun saySomething(
        something: String,
        id: String = "ID",
        queueMode: Int = TextToSpeech.QUEUE_ADD
    ) {
        val speechStatus = mTTS.speak(something, queueMode, null, id)
        if (speechStatus == TextToSpeech.ERROR) {
            Toast.makeText(this, "Cant use the Text to speech.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        mTTS.shutdown()
        super.onDestroy()
    }

    override fun onPause() {
        mTTS.stop()
        super.onPause()
    }

    fun getSpeechInput(view: View) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())

        if (intent.resolveActivity(packageManager) != null) {
            startActivityForResult(intent, _sttIntent)
        } else {
            Toast.makeText(
                this,
                "Your Device Doesn't Support Speech Input",
                Toast.LENGTH_SHORT
            )
                .show()
        }
    }
}