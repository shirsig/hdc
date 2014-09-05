function getGenomeDataFromUrl($scope, $routeParams) {

	// parse Base64 encoded uri and get the file
	if ($routeParams.cacheId) {
		
        $.ajax({
            url: "https://localhost:5000/snp-snip/?id="+$routeParams.cacheId,
            success: function(data) {
                $scope.$apply(function(){

                    $scope.recordCount = Object.keys(data).length;

                    if ($scope.recordCount) {
                        $scope.imported = true;
                    } else {
                        $scope.importFailed = true;
                    }

                    // add some additional information to the records
                    for (i in data) {
                        var record = data[i];

                        record.count = Object.keys(record.snpMap).length;
                        var dateMatch = record.comment.match(/This data file generated by 23andMe at: (.+)/);
                        record.date = dateMatch ? dateMatch[1] : "N/A";
                        var buildMatch = record.comment.match(/More information on reference human assembly build (\d+)/);
                        record.build = buildMatch ? buildMatch[1] : "N/A"; 
                        var buildUrlMatch = record.comment.match(/(http:\/\/www\.ncbi\.nlm\.nih\.gov\/projects\/mapview\/map_search\.cgi?.+)/);
                        record.buildUrl = buildUrlMatch ? buildUrlMatch[1] : "";
                    }

                    $scope.recordNames = Object.keys(data);
                    $scope.selectedRecordName = $scope.recordNames[0];
                    $scope.records = data;

                });
            }
        });

	} else {
        $scope.importFailed = true;
    }

}

function prepareSearchResults($scope, $sce, rs) {

    $scope.invalidInput = !$scope.isValidRs(rs);

    if ($scope.isValidRs(rs)) {

        $scope.rs = rs;

        // tabs
        var index = $scope.searches.map(function(search){return search.rs}).indexOf(rs);
        if (index === -1) {
            $scope.searches.unshift({rs: rs, active: true});
        } else {
            for (i in $scope.searches) {
                $scope.searches[i].active = false;
            }
            $scope.searches[index].active = true;
        }

        // get the required data if searching for this rs for the first time
        if (!$scope.data.hasOwnProperty(rs)) {
            $scope.data[rs] = {};

            var response;

            // get all data from the node server
            $.ajax({
                    url: "https://localhost:5000/snp-snip/?rs="+rs,
                    success: function(response) {
                        $scope.$apply(function() {

                            $scope.data[rs].resources = Object.keys(response).sort(function(r1, r2){
                                if (!(modules[r1] && modules[r2])) {
                                    return 0;
                                } else {
                                    return (modules[r1].position < modules[r2].position) ? -1 : 1;
                                }
                            });
                            var resources = Object.keys(response).sort(function(r1, r2){
                                if (!(modules[r1] && modules[r2])) {
                                    return 0;
                                } else {
                                    return (modules[r1].priority < modules[r2].priority) ? 1 : -1;
                                }
                            });

                            // prepare the data received from the server
                            for (i in resources) {
                                if (modules.hasOwnProperty(resources[i])) {
                                    modules[resources[i]].handler($scope, response[resources[i]]);
                                }
                            }

                            // prepare personal genome data
                            $scope.data[rs].genotypes = {};
                            if ($scope.data[rs].snpediaOrientation === "minus") {
                                $scope.data[rs].orientation = "minus";
                            } else {
                                $scope.data[rs].orientation = "plus";
                            }
                            for (i in $scope.records) {
                                if ($scope.data[rs].snpediaOrientation === "minus") {
                                    $scope.data[rs].genotypes[i] = $scope.changeOrientation($scope.records[i].snpMap[rs]);
                                } else {
                                    $scope.data[rs].genotypes[i] = $scope.records[i].snpMap[rs];
                                }
                            }
                        });
                    }
            });
        }
    }
}

var controllers = angular.module('snpSnipControllers', ['ui.bootstrap']);
controllers.controller('SnpSnipCtrl', ['$scope', '$sce', '$routeParams', '$modal', '$log',
function($scope, $sce, $routeParams, $modal, $log) {

    // convenient way access these objects in module handlers
    $scope.angular = {'sce': $sce, 'routeParams': $routeParams, 'modal': $modal, 'log': $log};

    $scope.searches = [];
    $scope.data = {};
    $scope.recordNames = [];
    $scope.records = {};

    getGenomeDataFromUrl($scope, $routeParams);

    $scope.removeTab = function (index) {
        $scope.searches.splice(index, 1);
        if (index > 0) {
            $scope.searchUpdate($scope.searches[index - 1].rs);
        } else if (index < $scope.searches.length) {
            $scope.searchUpdate($scope.searches[index].rs);
        }
    };

    $scope.searchUpdate = function(rs) {
        prepareSearchResults($scope, $sce, rs);
    };

    $scope.help = function () {

        var modalInstance = $modal.open({
          templateUrl: 'views/help.html',
          size: 'lg',
        });

        modalInstance.result.then(function (selectedItem) {
                $scope.selected = selectedItem;
            }, function () {
                $log.info('Modal dismissed at: ' + new Date());
        });
    };

    $scope.changeOrientation = function(genotype) {
        var swap = {'A' : 'T', 'T' : 'A', 'C' : 'G', 'G' : 'C'};
        var types = genotype.split('');
        return swap[types[0]]+swap[types[1]];
    }

    $scope.isValidRs = function(rs) {
        return rs.match(/^rs\d+$/);
    }

}]);
