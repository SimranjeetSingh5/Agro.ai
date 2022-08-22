package com.example.plantdiseasedetector.activities

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.SnapHelper
import com.example.plantdiseasedetector.databinding.ActivityMainBinding
import com.github.dhaval2404.imagepicker.ImagePicker
import com.google.firebase.auth.FirebaseAuth
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.lang.Integer.max
import java.lang.Math.min
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


class MainActivity : ComponentActivity() {

    companion object {
        const val TAG = "PDD Test"
        const val REQUEST_IMAGE_CAPTURE: Int = 1
        private const val MAX_FONT_SIZE = 96F
    }
    private lateinit var mAuth:FirebaseAuth
    private lateinit var binding:ActivityMainBinding
    private lateinit var diseaseAdapter:DiseaseAdapter
    
    
    private lateinit var currentPhotoPath: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpRecyclerView()
        checkLoginStatus()

        binding.captureImageFab.setOnClickListener{
            try {
                getImageFromCamera()
            } catch (e: ActivityNotFoundException) {
                Log.e(TAG, e.message.toString())
            }
        }
        binding.toolbarCamIcon.setOnClickListener{
            try {
                getImageFromCamera()
            } catch (e: ActivityNotFoundException) {
                Log.e(TAG, e.message.toString())
            }
        }
        binding.uploadImageFab.setOnClickListener {
            try {
                getImageFromGallery()
            }catch (e:ActivityNotFoundException){
                Log.e(TAG, e.message.toString())
            }
        }
    }

    private fun getImageFromGallery() {
        ImagePicker.with(this)
            .galleryOnly()
            .createIntent { intent ->
                val photoFile: File? = try {
                    createImageFile()
                } catch (e: IOException) {
                    Log.e(TAG, e.message.toString())
                    null
                }
                photoFile?.also {
                    val photoUri: Uri = FileProvider.getUriForFile(
                        this,
                        "com.example.plantdiseasedetector.fileprovider",
                        it
                    )
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                    startForProfileImageResult.launch(intent)
                }
            }
    }


    private fun getImageFromCamera() {
        ImagePicker.with(this)
            .cameraOnly()
            .createIntent { intent ->
                val photoFile: File? = try {
                    createImageFile()
                } catch (e: IOException) {
                    Log.e(TAG, e.message.toString())
                    null
                }
                photoFile?.also {
                    val photoUri: Uri = FileProvider.getUriForFile(
                        this,
                        "com.example.plantdiseasedetector.fileprovider",
                        it
                    )
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                    startForProfileImageResult.launch(intent)
                }
            }
    }
    private val startForProfileImageResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            val resultCode = result.resultCode
            val data = result.data

            when (resultCode) {
                Activity.RESULT_OK -> {
                    //Image Uri will not be null for RESULT_OK
                    val fileUri = data?.data!!

                    binding.imageView.setImageURI(fileUri)
                    val image = getCapturedImage()

                    lifecycleScope.launch(Dispatchers.Default) { runObjectDetection(image) }
//                    setViewAndDetect(getCapturedImage())
                }
                ImagePicker.RESULT_ERROR -> {
                    Toast.makeText(this, ImagePicker.getError(data), Toast.LENGTH_SHORT).show()
                }
                else -> {
                    Toast.makeText(this, "Task Cancelled", Toast.LENGTH_SHORT).show()
                }
            }
        }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            // Save a file: path for use with ACTION_VIEW intents
            currentPhotoPath = absolutePath
        }
    }

    private fun getCapturedImage(): Bitmap {
        // Get the dimensions of the View
        val targetW: Int = binding.imageView.width
        val targetH: Int = binding.imageView.height

        val bmOptions = BitmapFactory.Options().apply {
            // Get the dimensions of the bitmap
            inJustDecodeBounds = true

            BitmapFactory.decodeFile(currentPhotoPath, this)

            val photoW = 300
            val photoH = 300

            // Determine how much to scale down the image
            val scaleFactor: Int = max(1, (photoW / targetW).coerceAtMost(photoH / targetH))

            // Decode the image file into a Bitmap sized to fill the View
            inJustDecodeBounds = false
            inSampleSize = scaleFactor
            inMutable = true
        }
        val exifInterface = ExifInterface(currentPhotoPath)
        val orientation = exifInterface.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_UNDEFINED
        )

        val bitmap = BitmapFactory.decodeFile(currentPhotoPath, bmOptions)
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> {
                rotateImage(bitmap, 90f)
            }
            ExifInterface.ORIENTATION_ROTATE_180 -> {
                rotateImage(bitmap, 180f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> {
                rotateImage(bitmap, 270f)
            }
            else -> {
                bitmap
            }
        }
    }
    private fun rotateImage(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(
            source, 0, 0, source.width, source.height,
            matrix, true
        )
    }


    private fun runObjectDetection(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)

        val localModel = LocalModel.Builder()
            .setAssetFilePath(
                "lite-model_disease-classification.tflite"
            )
            .build()
        val options = CustomObjectDetectorOptions.Builder(localModel)
            .setDetectorMode(CustomObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .setClassificationConfidenceThreshold(0.5f)
            .setMaxPerObjectLabelCount(3)
            .build()


        val objectDetector =
            ObjectDetection.getClient(options)

        objectDetector.process(image)
            .addOnFailureListener { }
            .addOnSuccessListener { res ->
                for (detectedObject in res) {
                    val boundingBox = detectedObject.boundingBox
                    for (label in detectedObject.labels) {
                        val text = label.text

                        DetectionResult(boundingBox, text)
                    }

                }
//                    Log.e("Results",results[0].labels[0].text)

                val diseaseList = ArrayList<Item>()
                var count = 0
                val resultToDisplay = res.map { result ->
//                // Get the top-1 category and craft the display text
                    val boundingBox = result.boundingBox
                    result.labels.map {
                        val text = "${it.text}, ${it.confidence.times(100).toInt()}%"

                        diseaseList.add(Item(count++,"Nothing",it.confidence.times(100).toInt(),it.text,"Something"))
                        // Create a data object to display the detection result
                        DetectionResult(boundingBox, text)
                    }
                }


                diseaseAdapter.differ.submitList(diseaseList)
                val imgWithResult = drawDetectionResult(bitmap, resultToDisplay)

                this.runOnUiThread {
                    binding.imageView.setImageBitmap(imgWithResult)
                }


            }
    }
    private fun drawDetectionResult(
        bitmap: Bitmap,
        detectionResults: List<List<DetectionResult>>
    ): Bitmap {
        val outputBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(outputBitmap)
        val pen = Paint()
        pen.textAlign = Paint.Align.LEFT

        detectionResults.forEach { result ->
            // draw bounding box
            result.forEach {
                pen.color = Color.RED
                pen.strokeWidth = 8F
                pen.style = Paint.Style.STROKE
                val box = it.boundingBox
                canvas.drawRect(box, pen)


                val tagSize = Rect(0, 0, 0, 0)

                // calculate the right font size
                pen.style = Paint.Style.FILL_AND_STROKE
                pen.color = Color.YELLOW
                pen.strokeWidth = 2F

                pen.textSize = MAX_FONT_SIZE
                pen.getTextBounds(it.text, 0, it.text.length, tagSize)
                val fontSize: Float = pen.textSize * box.width() / tagSize.width()

                // adjust the font size so texts are inside the bounding box
                if (fontSize < pen.textSize) pen.textSize = fontSize

                var margin = (box.width() - tagSize.width()) / 2.0F
                if (margin < 0F) margin = 0F
                canvas.drawText(
                    it.text, box.left + margin,
                    box.top + tagSize.height().times(1F), pen
                )
            }
        }
        return outputBitmap
    }


    private fun setUpRecyclerView() {
        diseaseAdapter = DiseaseAdapter(this@MainActivity)
        val snapHelper: SnapHelper = LinearSnapHelper()
        snapHelper.attachToRecyclerView(binding.recyclerView)
        binding.recyclerView.apply {
            val llm = LinearLayoutManager(context)
            llm.orientation = LinearLayoutManager.VERTICAL
            layoutManager = llm
            adapter = diseaseAdapter
        }
    }

    private fun checkLoginStatus() {

        mAuth = FirebaseAuth.getInstance()
        if (mAuth.currentUser==null){
            startActivity(Intent(this, SendOtpActivity::class.java))
        }

    }

    override fun onBackPressed() {
        val dialogClickListener =
            DialogInterface.OnClickListener { dialog, which ->
                when (which) {
                    DialogInterface.BUTTON_POSITIVE -> {
                        finishAffinity()
                    }
                    DialogInterface.BUTTON_NEGATIVE -> {
                        dialog.cancel()
                    }
                }
            }
        val builder = AlertDialog.Builder(this)
        builder.setMessage("Are you sure?").setPositiveButton("Yes", dialogClickListener)
            .setNegativeButton("No", dialogClickListener).show()
    }
    data class DetectionResult(val boundingBox: Rect, val text: String)
}
