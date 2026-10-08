package com.irlclash;
public final class GameModels {private GameModels(){} public record Register(String playerId,String username){} public record Location(String playerId,String username,double latitude,double longitude){} public record Near(String playerId,String username,long distanceMeters){} public record Match(boolean found,String battleId,Near opponent,String message){} }
