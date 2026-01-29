package com.nuvio.tv.data.repository

import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.core.network.safeApiCall
import com.nuvio.tv.data.mapper.toDomain
import com.nuvio.tv.data.remote.api.AddonApi
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.AddonStreams
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.repository.AddonRepository
import com.nuvio.tv.domain.repository.StreamRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class StreamRepositoryImpl @Inject constructor(
    private val api: AddonApi,
    private val addonRepository: AddonRepository
) : StreamRepository {

    override fun getStreamsFromAllAddons(
        type: String,
        videoId: String
    ): Flow<NetworkResult<List<AddonStreams>>> = flow {
        emit(NetworkResult.Loading)

        try {
            val addons = addonRepository.getInstalledAddons().first()
            
            // Filter addons that support streams for this type
            val streamAddons = addons.filter { addon ->
                addon.supportsStreamResource(type)
            }

            if (streamAddons.isEmpty()) {
                emit(NetworkResult.Success(emptyList()))
                return@flow
            }

            // Fetch streams from all addons in parallel
            val results = coroutineScope {
                streamAddons.map { addon ->
                    async {
                        try {
                            val streamsResult = getStreamsFromAddon(addon.baseUrl, type, videoId)
                            when (streamsResult) {
                                is NetworkResult.Success -> {
                                    if (streamsResult.data.isNotEmpty()) {
                                        AddonStreams(
                                            addonName = addon.name,
                                            addonLogo = addon.logo,
                                            streams = streamsResult.data
                                        )
                                    } else null
                                }
                                else -> null
                            }
                        } catch (e: Exception) {
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            emit(NetworkResult.Success(results))
        } catch (e: Exception) {
            emit(NetworkResult.Error(e.message ?: "Failed to fetch streams"))
        }
    }

    override suspend fun getStreamsFromAddon(
        baseUrl: String,
        type: String,
        videoId: String
    ): NetworkResult<List<Stream>> {
        val cleanBaseUrl = baseUrl.trimEnd('/')
        val streamUrl = "$cleanBaseUrl/stream/$type/$videoId.json"

        // First, get addon info for name and logo
        val addonResult = addonRepository.fetchAddon(baseUrl)
        val addonName = when (addonResult) {
            is NetworkResult.Success -> addonResult.data.name
            else -> "Unknown"
        }
        val addonLogo = when (addonResult) {
            is NetworkResult.Success -> addonResult.data.logo
            else -> null
        }

        return when (val result = safeApiCall { api.getStreams(streamUrl) }) {
            is NetworkResult.Success -> {
                val streams = result.data.streams?.map { 
                    it.toDomain(addonName, addonLogo) 
                } ?: emptyList()
                NetworkResult.Success(streams)
            }
            is NetworkResult.Error -> result
            NetworkResult.Loading -> NetworkResult.Loading
        }
    }

    /**
     * Check if addon supports stream resource for the given type
     */
    private fun Addon.supportsStreamResource(type: String): Boolean {
        return resources.any { resource ->
            resource.name == "stream" && 
            (resource.types.isEmpty() || resource.types.contains(type))
        }
    }
}
