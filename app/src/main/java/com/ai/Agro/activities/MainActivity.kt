package com.ai.Agro.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.SnapHelper
import com.ai.Agro.Utils.Utils
import com.ai.Agro.databinding.ActivityMainBinding
import com.ai.Agro.databinding.WelcomeDialogBinding
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
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min


class MainActivity : ComponentActivity() {

    companion object {
        const val TAG = "Issues"
        const val REQUEST_IMAGE_CAPTURE: Int = 1
        private const val MAX_FONT_SIZE = 96F
    }

    private var utils: Utils = Utils()
    private lateinit var dialog: Dialog
    private lateinit var mAuth: FirebaseAuth
    private lateinit var binding: ActivityMainBinding
    private lateinit var diseaseAdapter: DiseaseAdapter


    private lateinit var currentPhotoPath: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpRecyclerView()
        checkLoginStatus()
        checkFirstLogin()

        binding.uploadImageFab.setOnClickListener {
            ImagePicker.with(this)
                .start()
        }
        binding.toolbarCamIcon.setOnClickListener {
            ImagePicker.with(this)
                .start()
        }
    }

    private fun checkFirstLogin() {
        val firstrun = getSharedPreferences("PREFERENCE", MODE_PRIVATE).getBoolean("firstrun", true)
        if (firstrun) {
            welcomeDialog()
            getSharedPreferences("PREFERENCE", MODE_PRIVATE)
                .edit()
                .putBoolean("firstrun", false)
                .commit()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (resultCode) {
            Activity.RESULT_OK -> {
                val uri: Uri = data?.data!!
                val bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, uri)
                setViewAndDetect(bitmap)
            }
            ImagePicker.RESULT_ERROR -> {
                Toast.makeText(this, ImagePicker.getError(data), Toast.LENGTH_SHORT).show()
            }
            else -> {}
        }
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
    private fun welcomeDialog(){
        dialog = Dialog(this)
        val dialogMainBinding : WelcomeDialogBinding = WelcomeDialogBinding.inflate(layoutInflater)
        dialog.setContentView(dialogMainBinding.root)
        dialog.window!!.setBackgroundDrawable(
            ColorDrawable(
                Color.TRANSPARENT
            )
        )
        dialog.show()
        dialogMainBinding.animationView.playAnimation()
        dialogMainBinding.thanksButton.setOnClickListener { // get count from text view
            dialog.dismiss()
        }
    }

    private fun checkLoginStatus() {

        mAuth = FirebaseAuth.getInstance()
        if (mAuth.currentUser == null) {
            startActivity(Intent(this, SendOtpActivity::class.java))
        }

    }
    private val takePicture =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success: Boolean ->
            if (success) {
                setViewAndDetect(getCapturedImage())
            }
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
            .addOnFailureListener {
                validation()
            }
            .addOnSuccessListener { res ->
                if (res.isEmpty() || res.size==0){
                    validation()
                }
                for (detectedObject in res) {
                    val boundingBox = detectedObject.boundingBox
                    for (label in detectedObject.labels) {
                        val text = label.text
                        DetectionResult(boundingBox, text)
                    }

                }

                val diseaseList = ArrayList<Item>()
                var count = 0
                val resultToDisplay = res.map { result ->
//                // Get the top-1 category and craft the display text
                    val boundingBox = result.boundingBox
                    result.labels.map {
                        val text = "${it.text}, ${it.confidence.times(100).toInt()}%"

                        diseaseList.add(
                            Item(
                                count++,
                                "Nothing",
                                it.confidence.times(100).toInt(),
                                it.text,
                                "Something"
                            )
                        )
                        // Create a data object to display the detection result
                        DetectionResult(boundingBox, text)
                    }
                }


                diseaseAdapter.differ.submitList(diseaseList)
                binding.infoText.visibility = View.GONE
                val imgWithResult = drawDetectionResult(bitmap, resultToDisplay)

                this.runOnUiThread {
                    binding.imageView.setImageBitmap(imgWithResult)
                }


            }
    }

    private fun validation() {
        utils.showDialog(this,
            Title = "No result Found",
            Message = "1.Try capturing in a more light up area\n" +
                    "2.Make sure to capture image with a blank background.\n" +
                    "3.High possibility of healthy crop",
            PositiveButton = "Retake",
            NegativeButton = "Cancel",
            listner = (DialogInterface.OnClickListener { dialogInterface, i ->
                try {
                    dispatchTakePictureIntent()
                } catch (e: ActivityNotFoundException) {
                    Log.e(TAG, e.message.toString())
                }
            }))
    }

    private fun setViewAndDetect(bitmap: Bitmap) {
        // Display capture image
        binding.imageView.setImageBitmap(bitmap)
//        binding.tvPlaceholder.visibility = View.INVISIBLE

        lifecycleScope.launch(Dispatchers.Default) { runObjectDetection(bitmap) }
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
            val scaleFactor: Int = max(1, min(photoW / targetW, photoH / targetH))

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

    //    private fun getSampleImage(drawable: Int): Bitmap {
//        return BitmapFactory.decodeResource(resources, drawable, BitmapFactory.Options().apply {
//            inMutable = true
//        })
//    }
    private fun rotateImage(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(
            source, 0, 0, source.width, source.height,
            matrix, true
        )
    }

    @SuppressLint("SimpleDateFormat")
    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name
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

    override fun onBackPressed() {
        utils.showDialog(this,
            Title = "Quitting app.Are you sure?",
            Message = null,
            PositiveButton = "Yes",
            NegativeButton = "Cancel",
            listner = (DialogInterface.OnClickListener { dialogInterface, i ->
                finishAffinity()
            }))
    }

    private fun dispatchTakePictureIntent() {
        takePicture.also { takePictureIntent ->
            // Create the File where the photo should go
            val photoFile: File? = try {
                createImageFile()
            } catch (e: IOException) {
                Log.e(TAG, e.message.toString())
                null
            }
            // Continue only if the File was successfully created
            photoFile?.also {
                val photoURI: Uri = FileProvider.getUriForFile(
                    this,
                    "com.ai.Agro.fileprovider",
                    it
                )
                takePictureIntent.launch(photoURI)
            }
        }
//        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
//            // Ensure that there's a camera activity to handle the intent
//            takePictureIntent.resolveActivity(packageManager)?.also {
//                // Create the File where the photo should go
//                val photoFile: File? = try {
//                    createImageFile()
//                } catch (e: IOException) {
//                    Log.e(TAG, e.message.toString())
//                    null
//                }
//                // Continue only if the File was successfully created
//                photoFile?.also {
//                    val photoURI: Uri = FileProvider.getUriForFile(
//                        this,
//                        "com.example.plantdiseasedetector.fileprovider",
//                        it
//                    )
//
//                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
//
//                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
//                }
//            }
//        }
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
}

data class DetectionResult(val boundingBox: Rect, val text: String)
