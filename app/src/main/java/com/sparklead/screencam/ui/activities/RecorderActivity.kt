package com.sparklead.screencam.ui.activities

import android.Manifest
import android.content.ContentValues
import android.content.DialogInterface
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.hbisoft.hbrecorder.HBRecorder
import com.hbisoft.hbrecorder.HBRecorderListener
import com.sparklead.screencam.R
import com.sparklead.screencam.databinding.ActivityRecorderBinding
import com.sparklead.screencam.services.BackgroundService
import com.sparklead.screencam.utils.Constants
import java.io.File
import java.sql.Date
import java.text.SimpleDateFormat
import java.util.*


class RecorderActivity : AppCompatActivity(), View.OnClickListener, HBRecorderListener {

    private lateinit var binding: ActivityRecorderBinding
    private var hasPermission = false
    private var hbRecorder: HBRecorder? = null
    private var highDefinition = true
    private var audioRecord = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Implemented data binding
        binding = ActivityRecorderBinding.inflate(layoutInflater)

        //Initialized hbrRecorder
        hbRecorder = HBRecorder(this, this)

        //Implemented onClickListener
        binding.ivRecord.setOnClickListener(this)
        binding.ivPause.setOnClickListener(this)

        setContentView(binding.root)

        binding.lifecycleOwner = this
    }

    override fun onClick(v: View?) {
        if (v != null) {
            when (v.id) {
                //Permission for Audio and write external storage
                (R.id.iv_record) -> {
                    checkAllPermission()
                }

                (R.id.iv_pause) -> {
                    hbRecorder!!.stopScreenRecording()
                }
            }
        }
    }

    private fun checkAllPermission() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.RECORD_AUDIO
            )
        }
        permissionLauncher.launch(permissions)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->

        var areAllGranted = true
        for (isGranted in result.values) {
            areAllGranted = areAllGranted && isGranted
        }
        hasPermission = areAllGranted

        if (areAllGranted) {
            startRecording()
        } else {
            Toast.makeText(this, "Permission denied...", Toast.LENGTH_SHORT).show()
            Constants.warningPermissionDialog(this) { _: DialogInterface, which: Int ->
                when (which) {
                    DialogInterface.BUTTON_POSITIVE ->
                        Constants.appSettingOpen(this)
                }
            }
        }
    }

    private fun getBasicOptions() {
        //switch for disable/enable
        binding.swEnableDisable.isChecked = highDefinition
        binding.swEnableDisable.setOnCheckedChangeListener { _, isChecked ->
            highDefinition = isChecked
        }
        //switch for disable/enable audio
        binding.swEnableDisableAudio.isChecked = audioRecord
        binding.swEnableDisableAudio.setOnCheckedChangeListener { _, isChecked ->
            audioRecord = isChecked
        }
    }

    private fun startRecording() {
        if (hbRecorder!!.isBusyRecording) {
            hbRecorder!!.stopScreenRecording()
        } else {
            startRecordingScreen()
        }
    }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            setOutputPath()
            // Start service before recording for Android 14+
            val serviceIntent = Intent(this, BackgroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            hbRecorder!!.startScreenRecording(result.data, result.resultCode)
        }
    }

    private fun startRecordingScreen() {
        //Implemented basic property for recorder
        getBasicOptions()
        quickSettings()
        //Implemented media projection manager to capture the contents of a device display
        val mediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val permissionIntent = mediaProjectionManager.createScreenCaptureIntent()
        screenCaptureLauncher.launch(permissionIntent)
    }


    private fun quickSettings() {
        // Basic property for video and audio
        hbRecorder!!.setAudioBitrate(128000)
        hbRecorder!!.setAudioSamplingRate(44100)
        hbRecorder!!.recordHDVideo(highDefinition)
        hbRecorder!!.isAudioEnabled(audioRecord)
        //Customise Notification for app
        hbRecorder!!.setNotificationSmallIcon(R.drawable.screencam)
        hbRecorder!!.setNotificationTitle("ScreenCam")
        hbRecorder!!.setNotificationDescription("ScreenCam is Recording")
    }


    private fun createFolder() {
        //Saved recorded to Movies folder with ScreenCam folder
        val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val folder = File(path, "ScreenCam")

        //if there is no such folder then create new folder
        if (!folder.exists()) {
            folder.mkdirs()
        }
    }




    private fun setOutputPath() {
        // Set system default time
        val formatter = SimpleDateFormat("dd-MM-yyyy-HH-mm-ss", Locale.getDefault())
        val curDate = Date(System.currentTimeMillis())
        val fileName = formatter.format(curDate).replace(" ", "")

        //Set Video title and type
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = contentResolver
            val contentValues = ContentValues()
            contentValues.put(MediaStore.Video.Media.TITLE, fileName)
            contentValues.put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            contentValues.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            contentValues.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ScreenCam")
            val mUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            //File name should be same
            hbRecorder!!.fileName = fileName
            hbRecorder!!.setOutputUri(mUri)
        } else {
            //Created folder
            createFolder()
            hbRecorder!!.setOutputPath(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                    .toString() + "/ScreenCam"
            )
        }
    }

    override fun HBRecorderOnStart() {
        binding.ivRecord.visibility = View.GONE
        binding.ivPause.visibility = View.VISIBLE
        Toast.makeText(this, "Recording Starts", Toast.LENGTH_SHORT).show()
    }

    override fun HBRecorderOnComplete() {
        //After screen recording complete
        stopService(Intent(this, BackgroundService::class.java))
        binding.ivPause.visibility = View.GONE
        binding.ivRecord.visibility = View.VISIBLE
        Toast.makeText(
            this,
            "Recording saved to Movies/ScreenCam folder Successfully",
            Toast.LENGTH_LONG
        ).show()
    }

    override fun HBRecorderOnPause() {
        // Implement if needed
    }

    override fun HBRecorderOnResume() {
        // Implement if needed
    }

    override fun HBRecorderOnError(errorCode: Int, reason: String?) {
        // After any exception
        Log.e("Error", reason.toString())
        Toast.makeText(this, "Error: $reason", Toast.LENGTH_SHORT).show()
        stopService(Intent(this, BackgroundService::class.java))
        binding.ivPause.visibility = View.GONE
        binding.ivRecord.visibility = View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        getBasicOptions()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.action == Constants.STOP_RECORDING_ACTION) {
            hbRecorder!!.stopScreenRecording()
        }
    }

}