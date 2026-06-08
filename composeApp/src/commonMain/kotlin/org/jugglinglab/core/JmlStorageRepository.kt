//
// JmlStorageRepository.kt
//
// Interface to define a repository for storing JML file data, and a
// concrete Okio implementation.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.core

import org.jugglinglab.jml.JmlPatternList
import org.jugglinglab.util.jlFileSystem
import okio.FileSystem
import okio.Path

interface JmlStorageRepository {
    fun saveList(path: Path, list: JmlPatternList)
    fun loadList(path: Path): JmlPatternList
    fun exists(path: Path): Boolean
    fun delete(path: Path)
    fun listFiles(directory: Path): List<Path>
    fun renameFile(oldPath: Path, newPath: Path)
    fun readFileText(path: Path): String
    fun writeFileText(path: Path, content: String)
    fun initializeFavorites(favoritesPath: Path, defaultContent: String)
}

class OkioJmlStorageRepository(private val fileSystem: FileSystem = jlFileSystem) : JmlStorageRepository {
    override fun saveList(path: Path, list: JmlPatternList) {
        fileSystem.write(path) {
            val sb = StringBuilder()
            list.writeJml(sb)
            writeUtf8(sb.toString())
        }
    }

    override fun loadList(path: Path): JmlPatternList {
        val text = fileSystem.read(path) { readUtf8() }
        val parser = org.jugglinglab.jml.JmlParser()
        parser.parse(text)
        if (parser.fileType == org.jugglinglab.jml.JmlParser.JML_LIST) {
            return JmlPatternList(parser.tree)
        } else {
            throw IllegalArgumentException("File is not a valid JML pattern list")
        }
    }

    override fun exists(path: Path): Boolean {
        return fileSystem.exists(path)
    }

    override fun delete(path: Path) {
        fileSystem.delete(path)
    }

    override fun listFiles(directory: Path): List<Path> {
        return if (fileSystem.exists(directory)) {
            fileSystem.list(directory)
        } else emptyList()
    }

    override fun renameFile(oldPath: Path, newPath: Path) {
        fileSystem.atomicMove(oldPath, newPath)
    }

    override fun readFileText(path: Path): String {
        return fileSystem.read(path) { readUtf8() }
    }

    override fun writeFileText(path: Path, content: String) {
        fileSystem.write(path) {
            writeUtf8(content)
        }
    }

    override fun initializeFavorites(favoritesPath: Path, defaultContent: String) {
        if (!fileSystem.exists(favoritesPath)) {
            fileSystem.write(favoritesPath) {
                writeUtf8(defaultContent)
            }
        }
    }
}
