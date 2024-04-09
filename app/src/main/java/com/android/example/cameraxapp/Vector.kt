package com.android.example.cameraxapp

import kotlin.math.sqrt

data class Vector(val x: Double, val y: Double, val z: Double) {

    constructor(q: Quaternion): this(q.x, q.y, q.z) { }

    fun scalarProd(b:Vector): Double {
        return this.x * b.x + this.y * b.y + this.z * b.z
    }

    fun vectorProd(b:Vector): Vector {
        return Vector(this.y * b.z - this.z * b.y,
            this.z * b.x - this.x * b.z,
            this.x * b.y - this.y * b.x)
    }

    fun norm(): Double {
        return sqrt(this.scalarProd(this))
    }

    operator fun times(other: Double): Vector {
        return Vector(x * other, y * other, z * other)
    }

    fun normalize(): Vector {
        val norm = this.norm()
        return Vector(this.x/norm, this.y/norm, this.z/norm)
    }

    fun DEStoNED(): Vector {
        return Vector(-this.z, this.y, this.x).normalize()
    }

}