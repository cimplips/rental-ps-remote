private fun startTvServer() {

    tvServer =
        TvServer(
            port = 8787,

            onCommand = { command ->

                handleCommand(command)
            },

            onStatusRequest = {

                val currentTime =
                    System.currentTimeMillis()

                val endTime =
                    sessionEndTimeMillis

                if (
                    endTime > currentTime
                ) {

                    "STATUS|ACTIVE|$endTime"

                } else {

                    "STATUS|IDLE|0"
                }
            }
        )

    tvServer?.start()
}
