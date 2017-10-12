package me.coeuvre.poju

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
class PojuApplication

fun main(args: Array<String>) {
    SpringApplication.run(PojuApplication::class.java, *args)
}
