package com.example.myapplication.model.collidables.active;

import android.util.Log;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.example.myapplication.model.Active;
import com.example.myapplication.model.Vector;
import com.example.myapplication.model.collidables.Collidable;
import com.example.myapplication.model.collidables.Field;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

public class Circle extends Active implements Collidable {

    private static final String COLLISION_TAG = "Collision";
    private static final String STATE_TAG = "Circle state";

    protected static final int MOVING_DELAY = 15; //15; //ms
    protected static final double MOVING_INCREMENT = 0.03; //0.03;
    private static final double SPEED_ROUND_LIMIT = 0.05;

    protected double mass;
    protected Vector center;
    protected Vector speed;

    private double radius;
    private double img_radius_coefficient;
    private double img_radius;

    private Field field;

    private HashMap<String, Double> collision_in_process;
    private HashMap<Integer, Circle> old;

    private int id;

    public Circle(double mass, double radius, double img_radius_coefficient, Vector center, Vector speed, @NotNull Field field) {
        this(mass, radius, img_radius_coefficient, center, speed, field, true, null, null);
    }

    public Circle(@NotNull Circle circle) {
        this(circle, true);
    }

    public Circle(double mass, double radius, double img_radius_coefficient, @NotNull Vector center, @NotNull Field field) {
        this(mass, radius, img_radius_coefficient, center, new Vector(0, 0), field);
    }

    public Circle(@NotNull Circle circle, @NotNull Field field) {
        this(circle.mass, circle.radius, circle.img_radius_coefficient, circle.center, circle.speed, field);
    }

    protected Circle(@NotNull Circle circle, boolean include) {
        this(circle.mass, circle.radius, circle.img_radius_coefficient, circle.center, circle.speed, circle.field, include, circle.collision_in_process, circle.old);
    }

    protected Circle(double mass, double radius, double img_radius_coefficient, @NotNull Vector center, Vector speed, @NotNull Field field, boolean include,
                     HashMap<String, Double> collision_in_process, HashMap<Integer, Circle> old) {
        setMass(mass);
        this.img_radius_coefficient = img_radius_coefficient;
        setRadius(radius);
        setCenter(new Vector(center));
        setSpeed(new Vector(speed));
        setField(field);
        id = field.getNextId();
        if (include) {
            field.addCollidable(this);
        }
        this.collision_in_process = (collision_in_process != null) ? new HashMap<>(collision_in_process) : new HashMap<String, Double>();
        this.old = (old != null) ? new HashMap<>(old) : new HashMap<Integer, Circle>();
    }

    private synchronized Circle getIdenticalNonInclusiveCopy() {
        Circle copy = getNonInclusiveCopy();
        copy.id = id;
        field.decrementId();
        return copy;
    }

    public int getCircleId() {
        return id;
    }

    public synchronized double getMass() {
        return mass;
    }

    public synchronized void setMass(double mass) {
        this.mass = mass;
    }

    public synchronized Field getField() {
        return field;
    }

    public synchronized void setField(Field field) {
        this.field = field;
    }

    public synchronized Vector getCenter() {
        return center;
    }

    public synchronized void setCenter(Vector center) {
        this.center = center;
    }

    public synchronized Vector getSpeed() {
        return speed;
    }

    public synchronized void setSpeed(Vector speed) {
        this.speed = speed;
        if (speed.intensity() < SPEED_ROUND_LIMIT)
            clearSpeed();
        else
            notifyAll();
    }

    public void setRadius(double radius) {
        this.radius = radius;
        img_radius = radius*img_radius_coefficient;
    }

    public double getImgRadius() {
        return img_radius;
    }

    @Override
    public String toString() {
        return "Circle " + getCircleId();
    }

    public void draw(ImageView view) {
        if (view == null) return;
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
        params.leftMargin = (int) (getCenter().getX() - getImgRadius());
        params.topMargin = (int) (getCenter().getY() - getImgRadius());
        view.setLayoutParams(params);
    }

    protected Circle getNonInclusiveCopy() {
        return new Circle(this, false);
    }
    public double getRadius() {
        return radius;
    }

    @Override
    public synchronized double getDistance(Circle circle) {
        if (circle == null) return 1;

        return center.sub(circle.center).intensity() - (getRadius() + circle.getRadius());
    }

    public synchronized double getDistance(Vector dot) {
        if (dot == null) return 1;

        return center.sub(dot).intensity() - getRadius();
    }

    public boolean isInside(Vector dot) {
        return getDistance(dot) <= 0;
    }

    public synchronized void clearSpeed() {
        speed.clear();
    }

    @Override
    public synchronized Collidable beforeCollision(Circle circle) {
        if (circle == null) return this;

        Circle old_collided = circle.old.get(id);
        if (old_collided != null)
            return old_collided;
        else
            return this;
    }

    @Override
    public synchronized void duringCollision(Circle circle) {
        if (circle == null) return;

        Circle old_collided = circle.old.get(id);
        if (old_collided == null) {

            Circle copy = circle.getIdenticalNonInclusiveCopy();
            old.put(circle.id, copy);
            notifyAll();

        } else
            circle.old.remove(id);
    }

    private void collision(Collidable collided) {
        if (collided == null) return;

        synchronized (field.getCircles()) {
            collided = collided.beforeCollision(this);
            double distance = collided.getDistance(this);
            Double old_distance = collision_in_process.get(collided.toString());

            if (distance <= 0) {
                collided.duringCollision(this);
                collision_in_process.put(collided.toString(), distance);

                if (old_distance == null || (old_distance != null && distance < old_distance)) {
                    collided.collisionUpdateSpeed(this);
                    Log.d(COLLISION_TAG, this + " collided " + collided);

                } else if (old_distance != null)// (distance >= old_distance) {
                    Log.d(COLLISION_TAG, this + " recovering from collision with " + collided);

            } else {
                if (old_distance != null) {
                    collision_in_process.remove(collided.toString());
                    Log.d(COLLISION_TAG, "Collision status for " + this + " and " + collided + " reset");
                }
            }
        }
    }

    @Override
    public synchronized void collisionUpdateSpeed(Circle collided) {
        if (collided == null) return;
        if (speed.isZeroVector() && collided.speed.isZeroVector()) return;

        collided.setSpeed(collided.speed.sub(
                collided.center.sub(center).mul(
                        2 * mass / (collided.mass + mass) *
                                ((collided.speed.sub(speed).dotProduct(collided.center.sub(center))) / Math.pow(collided.center.sub(center).intensity(), 2))
                )
        ));
    }

    private void checkCollision() {
        for (Collidable collidable : field.getCollidables()) {
            if (this != collidable) {
                collision(collidable);
            }
        }
    }

    private synchronized void checkSpeed() throws InterruptedException {

        if (field.checkStopped(this)) {
            Log.d(STATE_TAG, this + " stopped");
        }

        if (speed.isZeroVector()) {
            wait();
        }

        if (field.checkStarted(this)) {
            Log.d(STATE_TAG, this + " is moving");
        }
    }

    private synchronized void move() {
        center = center.add(speed);
        setSpeed(speed.mul(1 - field.getFrictionCoefficient()));
    }

    protected void work() {}

    protected void delay() throws InterruptedException {
        sleep(MOVING_DELAY);
    }

    @Override
    protected void iterate() {
        try {
            checkCollision();
            checkSpeed();
            if (!speed.isZeroVector()) {
                move();
                work();
                delay();
                field.barrier(this);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}