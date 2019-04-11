package com.example.myapplication.model.collidables;

import com.example.myapplication.model.Vector;
import com.example.myapplication.model.collidables.active.ActiveObject;

public interface Collidable {

    double getDistance(ActiveObject active);

    void collisionUpdateSpeed(ActiveObject active); //ActiveObject

    Collidable beforeCollision(ActiveObject active);

    void duringCollision(ActiveObject active);
}