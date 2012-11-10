(ns clojure-tetris.core
  (:use clojure.set)
  (:import (java.awt Color Dimension)
           (javax.swing JPanel JFrame Timer JOptionPane)
           (java.awt.event ActionListener KeyListener))
  (:gen-class))


(def gameplan-height 15)
(def gameplan-width 9)
(def block-size 25)
(def turn-millis 300)                             


(def VK_LEFT java.awt.event.KeyEvent/VK_LEFT)
(def VK_RIGHT java.awt.event.KeyEvent/VK_RIGHT)
(def VK_UP java.awt.event.KeyEvent/VK_UP)
(def VK_DOWN java.awt.event.KeyEvent/VK_DOWN)
(def VK_SPACE java.awt.event.KeyEvent/VK_SPACE)
(def VK_ENTER java.awt.event.KeyEvent/VK_ENTER)


(def rots { :clockwise [ [0 -1]
                         [1  0] ] 
           :cclockwise [ [0  1]
                         [-1 0] ] })
(def dirs {:left   [-1 0]
           :right  [1  0]
           :down  [0  1]})


(def blocks [ { :blocks 
               #{ [-1 -1] [0 -1] 
                          [0  0] 
                          [0  1] } }
             
              { :blocks 
               #{         [0  -1]
                  [-1 0 ] [0  0] [1 0] } }
              
              { :blocks 
               #{         [0 -1] [1 -1]
                          [0  0]
                          [0  1] } }
             
              { :blocks 
               #{ [-1 -1] [0 -1]
                          [0  0] [1  0]} }
             
              { :blocks
               #{         [0 -1] [1 -1]
                  [-1  0] [0  0] } }
             
              { :blocks 
               #{ [-2 0] [-1 0] [0 0] [1 0] } }
             
              { :blocks 
               #{ [-1 0] [0 0]
                  [-1 1] [0 1] } 
                :rotatable false } ])


(def input-to-keyword {VK_LEFT :left
                       VK_RIGHT :right
                       VK_DOWN :down
                       VK_UP :clockwise
                       VK_SPACE :cclockwise})
                       


(defn vec-add [ [x y] [a b] ]
  [(+ x a) (+ y b)])

(defn vec-rot [ [x y :as vec] [ [a b] [c d] :as mat]]
  [(+ (* x a) (* y b)) (+ (* x c) (* y d))])


(defprotocol Blockable
  (rotate [this rot])
  (move [this dir])
  (get-gameplan-blocks [this]))


(defrecord Block [pos innerblocks rotatable]
  Blockable
  (rotate [this rot]
    (if (true? (:rotatable this))
      (assoc this :innerblocks
              (set (map vec-rot (:innerblocks this) (repeat (rot rots)))))
      this))
  (move [this dir]
    (update-in this [:pos] vec-add (dir dirs)))
  (get-gameplan-blocks [this]
    (set (map vec-add (:innerblocks this) (repeat (:pos this))))))


(defn create-block []
  (let [{:keys [blocks rotatable] :or {rotatable true}} (nth blocks (rand-int (count blocks)))]
  (assoc 
    (->Block [(int (/ gameplan-width 2)) 0] blocks rotatable)
    :type :block
    :color (Color. 255 0 0))))

(defn create-outer-bounds []
  (union
    (set (for [y (range (inc gameplan-height))]
           [0 y]))
    (set (for [y (range (inc gameplan-height))]
           [gameplan-width y]))
    (set (for [x (range (inc gameplan-width))]
           [x gameplan-height]))))

(defn create-gameplan []
  {:gameplan (create-outer-bounds)
   :type :gameplan 
   :color (Color. 0 255 255)} )


(defn intersects? [block gameplan]
  (not (empty? (intersection (set (get-gameplan-blocks block)) (:gameplan gameplan)))))


(defn get-row [y]
  (set (for [x (range (inc gameplan-width))]
         [x y])))

(defn remove-row [gameplan row]
  "Will remove the specified row from the gameplan, and move all blocks above it down one step."
  (loop [y row
         new-gameplan (assoc gameplan :gameplan (difference (:gameplan gameplan)
                                                            (get-row row)))]
    (if (< y 0)
      new-gameplan
      (let [this-row (filter (fn [[_ h]] (= y h)) (:gameplan new-gameplan))
            gameplan-with-row-moved-down 
            (assoc new-gameplan :gameplan
                   (union 
                     (difference (:gameplan new-gameplan) this-row)
                     (set (map (fn [[x y]] [x (inc y)]) this-row))
                     (create-outer-bounds)))]
        (recur (dec y) gameplan-with-row-moved-down)))))
          

(defn update-gameplan-row [gameplan row]
  "If the specified row is full in the gameplan, then a gameplan with that row removed is returned. Otherwise, the gameplan is returned unchanged."
  (let [current-row-filled (get-row row)]
    (if (not (subset? current-row-filled (:gameplan gameplan)))
      gameplan
      ;we recur in case another full row falls into this row
      (recur (remove-row gameplan row) row)))) 

(defn check-for-rows [gameplan]
  (loop [y (dec gameplan-height)
         new-gameplan gameplan]
    (if (< y 0)
      new-gameplan
      (recur (dec y) (update-gameplan-row new-gameplan y)))))
  

(defn merge-block-into-gameplan [gameplan block]
  (assoc gameplan :gameplan
         (union
           (:gameplan gameplan)
           (get-gameplan-blocks block))))






; start to mix in some state from now on


(defn reset-game [block gameplan]
  (dosync
    (ref-set block (create-block))
    (ref-set gameplan (create-gameplan))))


(defn update-game [block gameplan]
  "Moves the block down, if possible. If the block collides with another block
   when moving down, we don't move it down but rather makes it part of the gameplan,
   and then sets it to a new block."
  (dosync
    (let [block-moved-down (move @block :down)]
      (if (intersects? block-moved-down @gameplan)
        (let [newblock (create-block)]
          (alter gameplan merge-block-into-gameplan @block)
          (if (intersects? newblock @gameplan)
            (alter block assoc :game-over true)
            (do 
              (ref-set block newblock)
              (alter gameplan check-for-rows))))
        (ref-set block block-moved-down)))))


(defn move-block [block dir gameplan]
  (dosync
    (let [block-moved (move @block dir)]
      (if (intersects? block-moved @gameplan)
        nil
        (do
          (ref-set block block-moved)
          (alter gameplan check-for-rows))))))

(defn rotate-block [block rot gameplan]
  (dosync
    (let [block-rotated (rotate @block rot)]
      (if (intersects? block-rotated @gameplan)
        nil
        (do
          (ref-set block block-rotated)
          (alter gameplan check-for-rows))))))






; Here be the gui

(def timer (atom nil))

(defn point-to-screen-rect [[x y]]
  [(* block-size x) (* block-size y) block-size block-size])

(defn fill-point [g pt color]
  (let [ [x y width height] (point-to-screen-rect pt)]
    (.setColor g color)
    (.fillRect g x y width height)
    (.setColor g (Color. 0 0 0))
    (.drawRect g x y width height)))

(defmulti paint (fn [g object & _] (:type object)))

(defmethod paint :block [g block]
  (doseq [b (get-gameplan-blocks block)]
    (fill-point g b (:color block))))

(defmethod paint :gameplan [g gameplan]
  (doseq [b (:gameplan gameplan)]
    (fill-point g b (:color gameplan))))


(defn handle-input [keycode block gameplan]
  (cond
    (some #(= % keycode) [VK_LEFT VK_DOWN VK_RIGHT])
      (move-block block (input-to-keyword keycode) gameplan)
    (some #(= % keycode) [VK_UP VK_SPACE])
      (rotate-block block (input-to-keyword keycode) gameplan)
    (= keycode VK_ENTER)
    (if (.isRunning @timer)
      (.stop @timer)
      (.restart @timer))))

(defn game-panel [frame block gameplan]
  (proxy [JPanel ActionListener KeyListener] []
    (paintComponent [g]
      (proxy-super paintComponent g)
      (paint g @block)
      (paint g @gameplan))
    (actionPerformed [e]
      (update-game block gameplan)
      (if (true? (:game-over @block))
            (do
              (JOptionPane/showMessageDialog frame "You lose!")
              (reset-game block gameplan)))
      (.repaint this))
    (keyPressed [e]
      (handle-input (.getKeyCode e) block gameplan)
      (.repaint this))
    (getPreferredSize []
      (Dimension. (* (inc gameplan-width) block-size)
                  (* (inc gameplan-height) block-size)))
    (keyReleased [e])
    (keyTyped [e])))


(defn game 
  [& {:keys [close-operation] :or {close-operation JFrame/EXIT_ON_CLOSE} :as opts}]
  (let [block (ref (create-block))
        gameplan (ref (create-gameplan))
        frame (JFrame. "Tetris")
        panel (game-panel frame block gameplan)]
    (reset! timer (Timer. turn-millis panel))
    (doto panel
      (.setFocusable true)
      (.addKeyListener panel))
    (doto frame
      (.add panel)
      (.pack)
      (.setDefaultCloseOperation close-operation)
      (.setVisible true))
    (.start @timer)
    frame))
    
(defn -main [& m]
  (game))