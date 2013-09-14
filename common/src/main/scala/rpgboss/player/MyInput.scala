package rpgboss.player
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.Input._
import com.badlogic.gdx.utils.Timer

trait InputHandler {
  import MyKeys._
  
  def keyDown(key: Int) = {}
  def keyUp(key: Int) = {}
  
  // Defines list of keys swallowed by this event handler
  val capturedKeys = Set(Up, Down, Left, Right, OK, Cancel)
}

trait PlayerInputHandler extends InputHandler {
  private val keyIsActiveMap = new Array[Boolean](MyKeys.totalNumber)
  
  def keyIsActive(key: Int) = keyIsActiveMap(key)
  
  override def keyDown(key: Int) = {
    keyIsActiveMap(key) = true
    keyActivated(key)
  }
  override def keyUp(key: Int) = {
    keyIsActiveMap(key) = false
    keyDeactivated(key)
  }
  
  def keyActivated(key: Int)
  def keyDeactivated(key: Int)
}

object MyKeys {
  val Up = 0
  val Down = 1
  val Left = 2
  val Right = 3
  val OK = 4
  val Cancel = 5

  val totalNumber = 6
}

/**
 * The whole purpose of this input handler is to send one "activation"
 * of the key on initial key press, and then if it's still held down,
 * send repeated activations at regular intervals until the key goes up.
 *
 * This is useful for scrolling through menu items.
 */
trait ChoiceInputHandler extends InputHandler {
  import MyKeys._
  def keyDelay: Float = 0.5f
  def keyInterval: Float = 0.1f

  private val activateTasks = (0 until MyKeys.totalNumber) map { key =>
    new Timer.Task() {
      def run() = {
        keyActivate(key)
      }
    }
  }

  override def keyDown(key: Int) = {
    // Initial activation
    keyActivate(key)

    // Schedule a task to be repeated 
    Timer.schedule(activateTasks(key), keyDelay, keyInterval)
  }

  override def keyUp(key: Int) = {
    activateTasks(key).cancel()
  }

  def keyActivate(key: Int)
}

/**
 * As in libgdx, delegation stops once a handler returns true
 */
class MyInputMultiplexer extends InputAdapter {
  val inputProcessors = new scala.collection.mutable.ListBuffer[InputHandler]()

  def hasFocus(handler: InputHandler) = 
    !inputProcessors.isEmpty && inputProcessors.head == handler
  
  // Maps 
  def mapKey(keycode: Int): Option[Int] = keycode match {
    case Keys.UP => Some(MyKeys.Up)
    case Keys.DOWN => Some(MyKeys.Down)
    case Keys.LEFT => Some(MyKeys.Left)
    case Keys.RIGHT => Some(MyKeys.Right)
    case Keys.SPACE => Some(MyKeys.OK)
    case Keys.M => Some(MyKeys.Cancel)
    case _ => None
  }
  
  private val keyIsActive = new Array[Boolean](MyKeys.totalNumber)
  
  override def keyDown(keycode: Int) = mapKey(keycode) map { key =>
    /*
     * This bit of hackery iterates through the whole list looking for a
     * handler that handles the input correctly.
     */
    keyIsActive(key) = true
    val handler = inputProcessors.find { _.capturedKeys.contains(key) }
    handler.map { _.keyDown(key) }.isDefined
  } getOrElse false

  override def keyUp(keycode: Int) = mapKey(keycode) map { key =>
    keyIsActive(key) = false
    val handler = inputProcessors.find { _.capturedKeys.contains(key) }
    handler.map { _.keyUp(key) }.isDefined
  } getOrElse false

  def prepend(newHandler: InputHandler) = {
    for (key <- newHandler.capturedKeys; if keyIsActive(key)) {
      // Send a keyUp signals to all input handlers that are now shadowed
      for (handler <- inputProcessors) {
        if (handler.capturedKeys.contains(key))
          handler.keyUp(key)
      }
      
      // Send a keyDown to the newly acquiring handler
      // Actually don't do this. This leads to un-intuitive behavior.
      // newHandler.keyDown(key)
    }

    inputProcessors.prepend(newHandler)
  }

  def remove(handler: InputHandler) = {
    for (key <- handler.capturedKeys; if keyIsActive(key)) {
      // Send a keyUp to the handler before removal
      handler.keyUp(key)
      
      // Send a keyDown to the next handler that's just been unshadowed.
      // Actually don't do this. This leads to un-intuitive behavior.
      // val nextKeyHandler = 
      //   inputProcessors.tail.find { _.capturedKeys.contains(key) }
      // nextKeyHandler.map { _.keyDown(key) }
    }
    
    inputProcessors -= handler
  }
}