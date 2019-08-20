package me.retrodaredevil.solarthing.android.prefs.json

import com.google.gson.JsonObject
import me.retrodaredevil.solarthing.android.prefs.ProfileManager
import java.util.*
import kotlin.NoSuchElementException

class JsonProfileManager<T>(
    private val jsonSaver: JsonSaver,
    private val newProfileJsonCreator: () -> JsonObject,
    private val profileCreator: (JsonSaver) -> T
) : ProfileManager<T> {

    private val profileMapCache: MutableMap<UUID, T> = HashMap()

    override fun getProfileName(uuid: UUID): String {
        return (getJsonProfile(uuid) ?: throw NoSuchElementException("No such profile with uuid: $uuid"))["name"].asString
    }

    override val profileUUIDs: List<UUID>
        get() {
            val array = jsonSaver.reloadedJsonObject["profiles"].asJsonArray
            val r = mutableListOf<UUID>()
            for(element in array){
                val jsonObject = element.asJsonObject
                r.add(UUID.fromString(jsonObject["uuid"].asString))
            }
            return r
        }
    override var activeUUID: UUID
        get() = UUID.fromString(jsonSaver.reloadedJsonObject["active"].asString)
        set(value) {
            jsonSaver.jsonObject.addProperty("active", value.toString())
            jsonSaver.save()
        }
    override val activeProfile: T
        get() = getProfile(activeUUID) ?: error("No profile with the active UUID!")
    override val activeProfileName: String
        get() = (getJsonProfile(activeUUID) ?: error("No json profile with active UUID!"))["name"].asString

    override fun removeProfile(uuid: UUID): Boolean {
        val array = jsonSaver.jsonObject["profiles"].asJsonArray
        for(element in array){
            val uuidString = element.asJsonObject["uuid"].asString
            if(uuidString == uuid.toString()){
                array.remove(element)
                jsonSaver.save()
                val success = profileMapCache.remove(uuid) != null
                if(!success) {
                    throw IllegalStateException("A profile was not cached in the map with uuid: $uuid")
                }
                return true
            }
        }
        return false
    }
    private fun getJsonProfile(uuid: UUID): JsonObject?{
        val uuidString = uuid.toString()
        val array = jsonSaver.jsonObject["profiles"].asJsonArray
        for(element in array){
            val jsonObject = element.asJsonObject
            if(jsonObject["uuid"].asString == uuidString){
                return jsonObject
            }
        }
        return null
    }

    override fun addAndCreateProfile(name: String): Pair<UUID, T> {
        val uuid = UUID.randomUUID()
        val array = jsonSaver.reloadedJsonObject["profiles"].asJsonArray
        val jsonObject = JsonObject()
        jsonObject.addProperty("name", name)
        jsonObject.addProperty("uuid", uuid.toString())
        val profileObject = newProfileJsonCreator()
        jsonObject.add("profile", profileObject)
        array.add(jsonObject)
        jsonSaver.save()

        val profile = profileCreator(NestedJsonSaver({ getJsonProfile(uuid)?.getAsJsonObject("profile") ?: error("No profile with uuid: $uuid") }, jsonSaver::reload, jsonSaver::save))
        profileMapCache[uuid] = profile
        return Pair(uuid, profile)
    }
    private fun reloadProfiles(){
        val array = jsonSaver.jsonObject["profiles"].asJsonArray
        println(array)
        for(element in array){
            val jsonObject = element.asJsonObject
            val uuid = UUID.fromString(jsonObject["uuid"].asString)
            if(uuid !in profileMapCache){
                val profile = profileCreator(NestedJsonSaver({ getJsonProfile(uuid)?.getAsJsonObject("profile") ?: error("No profile with uuid: $uuid") }, jsonSaver::reload, jsonSaver::save))
                profileMapCache[uuid] = profile
            }
        }
    }

    override fun setProfileName(uuid: UUID, name: String) {
        getJsonProfile(uuid)?.addProperty("name", name) ?: throw NoSuchElementException("No such uuid: $uuid map: $profileMapCache")
        jsonSaver.save()
    }

    override fun getProfile(uuid: UUID): T {
        return profileMapCache[uuid] ?: run {
            reloadProfiles()
            profileMapCache[uuid] ?: throw NoSuchElementException("No such uuid: $uuid map: $profileMapCache")
        }
    }

}