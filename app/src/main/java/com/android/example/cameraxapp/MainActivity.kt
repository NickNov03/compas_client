package com.android.example.cameraxapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Insets
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.util.TypedValue
import android.view.WindowInsets
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.android.example.cameraxapp.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.OnTokenCanceledListener
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt


class MainActivity : AppCompatActivity(), SensorEventListener {
    // фото
    lateinit var image : ByteArray

    // ширина и высота фото в пикселях (из битмапы)
    var width : Int = 0
    var height : Int = 0

    // sight angle (объект с углоами обзора камеры)
    lateinit var cam_params_manager : CamParams

    // сериализуемый объект кооридант, передаваемых с сервера
    lateinit var coords : Coords

    // широта и долгота
    var lat: Double = 0.0
    var lon: Double = 0.0

    // адрес сервера
    var url : String = "http://192.168.0.103:8080"

    var calc : Boolean = false

    var v_Img_Sun: Vector = Vector(0.0,0.0,0.0)
    var v_Eph_Sun: Vector = Vector(0.0,0.0,0.0)

    var q_ph_Sun : Quaternion = Quaternion(0.0,0.0,0.0,0.0)

    // кватернион вращения
    var q_rot : Quaternion = Quaternion(0.0,0.0,0.0,0.0)

    // кватернион разницы (доворота)
    var q_diff : Quaternion = Quaternion(0.0,0.0,0.0,0.0)

    val v_frontRUB = Vector(0.0, 0.0, -1.0)
    //var v_frontNED = Vector(0.0, 0.0, 0.0)

    private lateinit var viewBinding: ActivityMainBinding

    private var imageCapture: ImageCapture? = null

    private lateinit var cameraExecutor: ExecutorService

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var mSensorManager: SensorManager
    lateinit var accelerometer: Sensor
    lateinit var magnetometer: Sensor
    lateinit var rot: Sensor

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        cam_params_manager = CamParams(application)
        cam_params_manager.calculateFOV()


        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // Set up the listeners for take photo and video capture buttons
        viewBinding.imageCaptureButton.setOnClickListener { takePhoto() }

        cameraExecutor = Executors.newSingleThreadExecutor()

        // location
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        //sensor
        mSensorManager = getSystemService(SENSOR_SERVICE) as SensorManager;
        accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)!!
        magnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)!!
        rot = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)!!

        /*mSensorManager.registerListener(this, accelerometer, SENSOR_DELAY_UI)
        mSensorManager.registerListener(this, magnetometer, SENSOR_DELAY_UI)*/

        // костыль (1-й раз локация не работает))))
        get_Location()

        prepare_text(viewBinding.N)
        prepare_text(viewBinding.S)
        prepare_text(viewBinding.W)
        prepare_text(viewBinding.E)
        prepare_text(viewBinding.NE)
        prepare_text(viewBinding.SE)
        prepare_text(viewBinding.NW)
        prepare_text(viewBinding.SW)



        val metrics = windowManager.currentWindowMetrics
        // Gets all excluding insets
        // Gets all excluding insets
        val windowInsets = metrics.windowInsets
        val insets: Insets = windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.navigationBars()
                    or WindowInsets.Type.displayCutout()
        )

        val insetsWidth: Int = insets.right + insets.left
        val insetsHeight: Int = insets.top + insets.bottom

        // Legacy size that Display#getSize reports

        // Legacy size that Display#getSize reports
        val bounds = metrics.bounds
        val legacySize = Size(
            bounds.width() - insetsWidth,
            bounds.height() - insetsHeight
        )
        width = legacySize.width
        height = legacySize.height
        cam_params_manager.calculateFocal(width)
    }

    fun prepare_text(t :TextView) {
        t.setTextColor(Color.RED)
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP,25.0F)
    }

    override fun onResume() {
        super.onResume()
        try {
            mSensorManager.registerListener(this, accelerometer,SensorManager.SENSOR_DELAY_NORMAL)
            mSensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL)
            mSensorManager.registerListener(this, rot, SensorManager.SENSOR_DELAY_NORMAL)
        } catch (_: Exception) { }
    }

    override fun onPause() {
       super.onPause()
        mSensorManager.unregisterListener(this)
    }

    private fun get_Image() {
        val bitmap = viewBinding.viewFinder.bitmap
        width = bitmap?.width ?: return
        height = bitmap.height
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        image = stream.toByteArray()
    }

    private fun get_Location() {

        // какая-то требуемая проверка разрешений
        if (ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            return
        }

        // получаем локацию (lon, lat)
        fusedLocationClient.getCurrentLocation(PRIORITY_HIGH_ACCURACY, object : CancellationToken() {
            override fun onCanceledRequested(p0: OnTokenCanceledListener) = CancellationTokenSource().token
            override fun isCancellationRequested() = false
        })
            .addOnSuccessListener { location: Location? ->
                // если неудачно
                if (location == null) {
                    val msg = "Cannot get location."
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }
                // если удачно
                else {
                    lat = location.latitude
                    lon = location.longitude
                }
            }
    }

    private fun Send() : Boolean {
        var success  = true
        val response : HttpResponse
        runBlocking() {
            val client = HttpClient(CIO) {
                install(ContentNegotiation) {
                    json(Json {
                        prettyPrint = true
                        isLenient = true
                    })
                }
                expectSuccess = false
            }
            try {
                response = client.post(url) {
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append("lngtd", lon.toString())
                                append("lttd", lat.toString())
                                append("photo", image, Headers.build {
                                    append(HttpHeaders.ContentType, "image/jpg")
                                    append(HttpHeaders.ContentDisposition, "filename=\"img.jpg\"")
                                })
                            })
                    )
                }
                coords = response.body()
                if (coords.x == (-1).toFloat()) {
                    success = false
                    val msg = "Couldn't find a sun"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_LONG).show()
                    Log.d(TAG, msg)
                    return@runBlocking
                }

            } catch (exc: Exception) {
                success = false
                val msg = "No answer"
                Toast.makeText(baseContext, msg, Toast.LENGTH_LONG).show()
                Log.d(TAG, msg)
            }
        }

        return success
    }

    private fun calculation() {
        val a_hor: Float = calculate_a_hor() //good
        val a_ver: Float = calculate_a_ver() // good
        v_Img_Sun = coords.v_Img_RUB(a_hor, a_ver) // good
        v_Eph_Sun = coords.v_Eph_ENU() // good

        var v_ph_Sun = q_rot.rotate(v_Img_Sun)

        val norm_v_ph_Sun = v_ph_Sun.norm()
        val norm_v_Eph_Sun = v_Eph_Sun.norm()

        val scalProd = v_ph_Sun.scalarProd(v_Eph_Sun) // good
        val vectProd = v_ph_Sun.vectorProd(v_Eph_Sun) // good


        val cosAngle = scalProd / (norm_v_ph_Sun * norm_v_Eph_Sun)
        val sinAngle = vectProd.norm() / (norm_v_ph_Sun * norm_v_Eph_Sun)

        val cosHalfAngle = cos(acos(cosAngle) / 2)
        val sinHalfAngle = sin(asin(sinAngle) / 2) // good

        q_diff = Quaternion(cosHalfAngle.toDouble(), vectProd * sinHalfAngle)
        q_diff *= (1 / q_diff.abs())


        //v_ph_Sun = q_diff.rotate(v_ph_Sun)
        /*val msg = "Calculated"
        Toast.makeText(baseContext, msg, Toast.LENGTH_LONG).show()
        Log.d(TAG, msg)*/
        //var a = q_rot.rotate(v_Eph_Sun)
        //val msg = a.x.toString() + ' ' + a.y.toString() + ' ' + a.z.toString() + ' ' +
        //        v_Img_Sun.x.toString() + ' ' + v_Img_Sun.y.toString() + ' ' + v_Img_Sun.z.toString()
        //val msg = v_Eph_Sun.x.toString() + ' ' + v_Eph_Sun.y.toString() + ' ' + v_Eph_Sun.z.toString() + ' ' +
         //       v_ph_Sun.x.toString() + ' ' + v_ph_Sun.y.toString() + ' ' + v_ph_Sun.z.toString()
        val msg = q_diff.x.toString() + ' ' + q_diff.y.toString() + ' ' + q_diff.z.toString() + ' ' +
                       q_diff.w.toString() + q_diff.abs().toString()
        Toast.makeText(baseContext, msg, Toast.LENGTH_LONG).show()
        Log.d(TAG, msg)
        viewBinding.textView2.text = msg

        calc = true
    }

    private fun takePhoto() {
        imageCapture ?: return

        // get picture
        get_Image()

        // тут надо запомнить кватернион

        // получаем локацию (lon, lat)
        get_Location()

        // если передача данных успешна
         if (Send()) {
             calculation()
         }

    }


    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder().build()

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))


    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXApp"
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACTIVITY_RECOGNITION,
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions())
        { permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && it.value == false)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(baseContext,
                    "Permission request denied",
                    Toast.LENGTH_SHORT).show()
            } else {
                startCamera()
            }
        }

    private fun calculate_a_hor(): Float {
        return coords.a_hor(cam_params_manager.horizonalAngle, width)
    }

    private fun calculate_a_ver(): Float {
        return coords.a_ver(cam_params_manager.verticalAngle, height)
    }
    private fun DtR(x: Float): Float {
        return x * 180 / PI.toFloat()
    }

    lateinit var mGravity: FloatArray
    lateinit var mGeomagnetic: FloatArray
    lateinit var mRotationMatrix: FloatArray

    override fun onSensorChanged(event: SensorEvent) {

        try {
            if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                q_rot = Quaternion(event.values[3].toDouble(), event.values[0].toDouble(),
                    event.values[1].toDouble(), event.values[2].toDouble()) // ENU
                if (calc) {
                    val q = q_diff * q_rot
                    val orient = q.rotate(v_frontRUB)
                    draw(q)
                }
                else {
                    val orient = q_rot.rotate(v_frontRUB)
                    draw(q_rot)
                }

                /*var r = Vector(0.0F, 0.0F, -1.0F)
                val b = q_rot.rotate(r)
                var x = b.x
                var y = b.y
                var z = b.z

                viewBinding.textView2.text = String.format("%.3f,    %.3f,    %.3f", x, y, z)*/

                /*val a = event.values
                var x = q_rot.x
                var y = q_rot.y
                var z = q_rot.z
                var w = q_rot.w
                viewBinding.textView2.text = String.format("%.3f,    %.3f,    %.3f,    %.3f", x, y, z, w)*/
                /*if (calc) {
                    v_frontNED = q_diff.rotate(q_rot.rotate(v_frontFRD))
                    var x = v_frontNED.x
                    var y = v_frontNED.y
                    var z = v_frontNED.z
                    viewBinding.textView2.text = String.format("%.3f,    %.3f,    %.3f", x, y, z)
                }
                else {
                    v_frontNED = q_rot.rotate(v_frontFRD)
                    var x = v_frontNED.x
                    var y = v_frontNED.y
                    var z = v_frontNED.z
                    var w = q_rot.w
                    var a = q_rot.x
                    var b = q_rot.y
                    var c = q_rot.z
                    viewBinding.textView2.text = String.format("%.3f,    %.3f,    %.3f,    %.3f,    %.3f,    %.3f,    %.3f",
                        x, y, z, w, a,b,c)
                }*/
            }
            /*if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) mGravity = event.values
            if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) mGeomagnetic = event.values
            if (mGravity != null && mGeomagnetic != null) {
                val R = FloatArray(9)
                val I = FloatArray(9)
                val success = getRotationMatrix(R, I, mGravity, mGeomagnetic)
                if (success) {
                    val orientation = FloatArray(3)
                    getOrientation(R, orientation)
                    val q = FloatArray(4)
                    getQuaternionFromVector(q, orientation)
                    q_rot = Quaternion(q[0].toDouble(), q[2].toDouble(), q[1].toDouble(), -q[3].toDouble()) // NED
                    if (calc) {
                        v_frontNED = q_diff.rotate(q_rot.rotate(v_frontFRD))
                        var x = v_frontNED.x
                        var y = v_frontNED.y
                        var z = v_frontNED.z
                        viewBinding.textView2.text = String.format("%.3f,    %.3f,    %.3f", x, y, z)
                    }
                    else {
                        v_frontNED = q_rot.rotate(v_frontFRD)
                        var x = v_frontNED.x
                        var y = v_frontNED.y
                        var z = v_frontNED.z
                        var a = q_rot.x
                        var b = q_rot.y
                        var c = q_rot.z
                        viewBinding.textView2.text = String.format("%.3f,    %.3f,    %.3f,    %.3f,    %.3f,    %.3f",
                            x, y, z,a,b,c)
                    }

                }
            }*/
        } catch (_:Exception) { }

    }

    // ENU
    val N = Vector(0.0, 1.0, 0.0)
    val S = Vector(0.0, -1.0, 0.0)
    val W = Vector(-1.0, 0.0, 0.0)
    val E = Vector(1.0, 0.0, 0.0)
    val NE = Vector(sqrt(2.0) / 2, sqrt(2.0) / 2, 0.0)
    val SE = Vector(sqrt(2.0) / 2, -sqrt(2.0) / 2, 0.0)
    val NW = Vector(-sqrt(2.0) / 2, sqrt(2.0) / 2, 0.0)
    val SW = Vector(-sqrt(2.0) / 2, -sqrt(2.0) / 2, 0.0)

    fun draw(q: Quaternion) {
        // RUB
        var q_inv = q.invert()
        var n = q_inv.rotate(N)
        var s = q_inv.rotate(S)
        var w = q_inv.rotate(W)
        var e = q_inv.rotate(E)
        var ne = q_inv.rotate(NE)
        var se = q_inv.rotate(SE)
        var nw = q_inv.rotate(NW)
        var sw = q_inv.rotate(SW)

        /*n = Vector(-n.x/n.z, n.y/n.z, -1.0)

        n *= cam_params_manager.focal_len_pix.toDouble()
        val nx = n.x + width / 2
        val ny = n.y + height / 2
        val msg = n.x.toString() + " " + n.y.toString()
        viewBinding.textView2.text = msg
        viewBinding.N.x  = nx.toFloat()
        viewBinding.N.y = ny.toFloat()*/
        make_text(n , viewBinding.N)
        make_text(s , viewBinding.S)
        make_text(w , viewBinding.W)
        make_text(e , viewBinding.E)
        make_text(ne, viewBinding.NE)
        make_text(se, viewBinding.SE)
        make_text(nw, viewBinding.NW)
        make_text(sw, viewBinding.SW)
    }

    fun make_text(vec : Vector, t : TextView) {

        var v = Vector(-vec.x / vec.z, vec.y / vec.z, -1.0)

        v *= cam_params_manager.focal_len_pix.toDouble()
        val vx = v.x + width / 2
        val vy = v.y + height / 2
        t.x  = vx.toFloat()
        t.y = vy.toFloat()
        if (vec.z > 0)
            t.alpha = 0.0F
        else
            t.alpha = 1.0F
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { }

}

