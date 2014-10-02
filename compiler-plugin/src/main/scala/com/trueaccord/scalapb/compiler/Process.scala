package com.trueaccord.scalapb.compiler

import java.nio.file.attribute.PosixFilePermission
import java.nio.file.{Files, Path}

import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.sys.process._

object Process {
  def runProtoc(args: String*) = {
    val pipe = createPipe()
    val sh = createShellScript(pipe)

    Future {
      try {
        val fsin = Files.newInputStream(pipe)
        val request = CodeGeneratorRequest.parseFrom(fsin)
        val response = ProtobufGenerator.handleCodeGeneratorRequest(request)
        val fsout = Files.newOutputStream(pipe)
        fsout.write(response.toByteArray)
        fsout.close()
        fsin.close()
      } catch {
        case e: Exception =>
          println("Exc: ", e)
      }
    }

    (Seq("protoc",
      s"--plugin=protoc-gen-scala=$sh") ++ args).!!
    Files.delete(pipe)
    Files.delete(sh)
  }

  private def createPipe(): Path = {
    val pipeName = Files.createTempFile("protopipe-", ".pipe")
    Files.delete(pipeName)
    Seq("mkfifo", "-m", "600", pipeName.toAbsolutePath.toString).!!
    pipeName
  }

  private def createShellScript(tmpFile: Path): Path = {
    val content =
      s"""|#!/usr/bin/env sh
          |cat /dev/stdin > "$tmpFile"
          |cat "$tmpFile"
      """.stripMargin
    val scriptName = Files.createTempFile("compgen", "")
    val os = Files.newOutputStream(scriptName)
    os.write(content.getBytes("UTF-8"))
    os.close()
    Files.setPosixFilePermissions(scriptName, Set(
      PosixFilePermission.OWNER_EXECUTE,
      PosixFilePermission.OWNER_READ))
    scriptName
  }
}
