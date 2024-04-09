package com.android.example.cameraxapp

import kotlin.math.sqrt

class Quaternion(var w: Double, var x: Double, var y: Double, var z: Double) {

    constructor(_w : Double, vect:Vector) :
            this(_w, vect.x.toDouble(), vect.y.toDouble(), vect.z.toDouble()) { }

    operator fun plus(other: Quaternion): Quaternion {
        val neww = w + other.w
        val newx = x + other.x
        val newy = y + other.y
        val newz = z + other.z
        return Quaternion(neww, newx, newy, newz)
    }

    operator fun minus(other: Quaternion): Quaternion {
        val neww = w - other.w
        val newx = x - other.x
        val newy = y - other.y
        val newz = z - other.z
        return Quaternion(neww, newx, newy, newz)
    }

    operator fun times(other: Quaternion): Quaternion {
        val neww = w*other.w - x*other.x - y*other.y - z*other.z
        val newx = w*other.x + x*other.w + y*other.z - z*other.y
        val newy = w*other.y - x*other.z + y*other.w + z*other.x
        val newz = w*other.z + x*other.y - y*other.x + z*other.w
        return Quaternion(neww, newx, newy, newz)
    }

    operator fun times(other: Double): Quaternion {
        return Quaternion(w*other,x*other,y*other,z*other)
    }

    fun invert(): Quaternion {
        return this.conjugate() * (1 / abs())
    }

    fun rotate(vect : Vector): Vector  { // rotates vector by this
        val other = Quaternion(0.0, vect)
        return Vector(this * other * this.invert()).normalize()
    }

    fun norm() = norm(this)

    fun abs() = abs(this)

    fun conjugate() = conjugate(this)

    override fun equals(other: Any?): Boolean {
        val otherQuaternion = other as Quaternion
        return w == otherQuaternion.w && x == otherQuaternion.x && y == otherQuaternion.y
                && z == otherQuaternion.z
    }

    override fun hashCode(): Int {
        var result = w.hashCode()
        result = 31 * result + x.hashCode()
        result = 31 * result + y.hashCode()
        result = 31 * result + z.hashCode()
        return result
    }

    companion object {

        fun norm(quaternion: Quaternion) = Math.pow(quaternion.w, 2.0) + Math.pow(quaternion.x, 2.0) +
                Math.pow(quaternion.y, 2.0) + Math.pow(quaternion.z, 2.0)

        fun abs(quaternion: Quaternion) = Math.sqrt(norm(quaternion))

        fun conjugate(quaternion: Quaternion) = Quaternion(quaternion.w, -quaternion.x, -quaternion.y, -quaternion.z)
    }
}