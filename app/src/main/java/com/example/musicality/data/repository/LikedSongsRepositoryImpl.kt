package com.example.musicality.data.repository

import com.example.musicality.data.local.LikedSongDao
import com.example.musicality.data.local.LikedSongEntity
import com.example.musicality.domain.repository.LikedSongsRepository
import kotlinx.coroutines.flow.Flow

/**
 * Implementation of LikedSongsRepository using Room database
 */
class LikedSongsRepositoryImpl(
    private val likedSongDao: LikedSongDao
) : LikedSongsRepository {
    
    override fun getAllLikedSongs(): Flow<List<LikedSongEntity>> {
        return likedSongDao.getAllLikedSongs()
    }
    
    override fun isSongLiked(videoId: String): Flow<Boolean> {
        return likedSongDao.isSongLiked(videoId)
    }
    
    override fun getLikedSongsCount(): Flow<Int> {
        return likedSongDao.getLikedSongsCount()
    }
    
    override suspend fun toggleLike(song: LikedSongEntity) {
        likedSongDao.toggleLike(song)
    }
    
    override suspend fun likeSong(song: LikedSongEntity) {
        likedSongDao.upsertLikedSong(song)
    }
    
    override suspend fun unlikeSong(videoId: String) {
        likedSongDao.deleteLikedSong(videoId)
    }
}
