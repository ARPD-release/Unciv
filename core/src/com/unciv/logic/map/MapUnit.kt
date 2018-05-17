package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.automation.WorkerAutomation
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.Unit
import java.text.DecimalFormat

class MapUnit {
    @Transient
    lateinit var civInfo: CivilizationInfo

    lateinit var owner: String
    lateinit var name: String
    var maxMovement: Int = 0
    var currentMovement: Float = 0f
    var health:Int = 100
    var action: String? = null // work, automation, fortifying, I dunno what.

    fun getBaseUnit(): Unit = GameBasics.Units[name]!!
    fun getMovementString(): String = DecimalFormat("0.#").format(currentMovement.toDouble()) + "/" + maxMovement
    fun getTile(): TileInfo {
        return civInfo.gameInfo.tileMap.values.first{it.unit==this}
    }

    fun getDistanceToTiles(): HashMap<TileInfo, Float> {
        val tile = getTile()
        return movementAlgs().getDistanceToTilesWithinTurn(tile.position,currentMovement)
    }

    fun doPreTurnAction() {
        val currentTile = getTile()
        if (currentMovement == 0f) return  // We've already done stuff this turn, and can't do any more stuff

        val enemyUnitsInWalkingDistance = getDistanceToTiles().keys
                .filter { it.unit!=null && it.unit!!.civInfo!=civInfo }
        if(enemyUnitsInWalkingDistance.isNotEmpty()) return  // Don't you dare move.

        if (action != null && action!!.startsWith("moveTo")) {
            val destination = action!!.replace("moveTo ", "").split(",").dropLastWhile { it.isEmpty() }.toTypedArray()
            val destinationVector = Vector2(Integer.parseInt(destination[0]).toFloat(), Integer.parseInt(destination[1]).toFloat())
            val gotTo = movementAlgs().headTowards(currentTile.tileMap[destinationVector])
            if(gotTo==currentTile) // We didn't move at all
                return
            if (gotTo.position == destinationVector) action = null
            if (currentMovement != 0f) doPreTurnAction()
            return
        }

        if (action == "automation") WorkerAutomation().automateWorkerAction(this)
    }

    private fun doPostTurnAction() {
        if (name == "Worker" && getTile().improvementInProgress != null) workOnImprovement()
    }

    private fun workOnImprovement() {
        val tile=getTile()
        tile.turnsToImprovement -= 1
        if (tile.turnsToImprovement != 0) return
        when {
            tile.improvementInProgress!!.startsWith("Remove") -> tile.terrainFeature = null
            tile.improvementInProgress == "Road" -> tile.roadStatus = RoadStatus.Road
            tile.improvementInProgress == "Railroad" -> tile.roadStatus = RoadStatus.Railroad
            else -> tile.improvement = tile.improvementInProgress
        }
        tile.improvementInProgress = null
    }

    /**
     * @return The tile that we reached this turn
     */

    private fun heal(){
        val tile = getTile()
        health += when{
            tile.isCityCenter() -> 20
            tile.getOwner()?.civName == owner -> 15 // home territory
            tile.getOwner() == null -> 10 // no man's land (neutral)
            else -> 5 // enemy territory
        }
        if(health>100) health=100
    }


    fun moveToTile(otherTile: TileInfo) {
        val distanceToTiles = getDistanceToTiles()
        if (!distanceToTiles.containsKey(otherTile))
            throw Exception("You can't get there from here!")
        if (otherTile.unit != null ) throw Exception("Tile already contains a unit!")

        currentMovement -= distanceToTiles[otherTile]!!
        if (currentMovement < 0.1) currentMovement = 0f // silly floats which are "almost zero"
        getTile().unit = null
        otherTile.unit = this
    }

    fun endTurn() {
        doPostTurnAction()
        if(currentMovement==maxMovement.toFloat()){ // didn't move this turn
            heal()
        }
    }

    fun startTurn(){
        currentMovement = maxMovement.toFloat()
        doPreTurnAction()
    }

    fun hasUnique(unique:String): Boolean {
        val baseUnit = getBaseUnit()
        return baseUnit.uniques!=null && baseUnit.uniques!!.contains(unique)
    }

    fun movementAlgs() = UnitMovementAlgorithms(this)

    override fun toString(): String {
        return name +" - "+owner
    }
}